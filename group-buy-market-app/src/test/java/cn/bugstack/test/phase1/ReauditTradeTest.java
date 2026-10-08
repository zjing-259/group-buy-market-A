package cn.bugstack.test.phase1;

import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.*;
import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.types.exception.AppException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import javax.annotation.Resource;
import java.util.Date;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Real MySQL regression coverage for the four re-audit findings and adjacent boundaries. */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PhaseOneTradeTest.Config.class)
public class ReauditTradeTest {
    @Resource private JdbcTemplate jdbc;
    @Resource private ITradeLockOrderService lockService;
    @Resource private ITradeSettlementOrderService settlement;
    @Resource private PlatformTransactionManager transactionManager;
    @Resource private ITradeRepository repository;

    @Before public void seed() {
        PhaseOneTradeTest fixture = new PhaseOneTradeTest();
        ReflectionTestUtils.setField(fixture, "jdbc", jdbc);
        fixture.seed();
    }

    private MarketPayOrderEntity lock(String user, String out, String team) throws Exception {
        return lockService.lockMarketPayOrder(UserEntity.builder().userId(user).build(),
                PayActivityEntity.builder().activityId(900001L).teamId(team).build(),
                PayDiscountEntity.builder().source("s01").channel("c01").goodsId("testsku")
                        .outTradeNo(out).notifyUrl("http://127.0.0.1:1/never-called").build());
    }

    @Test public void differentExternalNumbersMustNotSilentlyReplayAnotherOrder() throws Exception {
        MarketPayOrderEntity first = lock("u", "CaseOrder", null);
        MarketPayOrderEntity second = lock("u", "caseorder", null);
        assertNotEquals("Different external IDs silently replayed the same order", first.getOrderId(), second.getOrderId());
    }

    @Test public void reservationMustNotSucceedAfterWaitingPastTeamDeadline() throws Exception {
        String team = lock("leader", "leader", null).getTeamId();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        final Future<?>[] joining = new Future<?>[1];
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select team_id from group_buy_order where team_id=? for update", String.class, team);
                jdbc.update("update group_buy_order set valid_end_time=now()+interval 2 second where team_id=?", team);
                CountDownLatch started = new CountDownLatch(1);
                joining[0] = pool.submit(() -> { started.countDown(); return lock("member", "member", team); });
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    Thread.sleep(3500);
                    assertFalse("Join should be waiting for the team's row lock", joining[0].isDone());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return null;
            });
            try {
                joining[0].get(10, TimeUnit.SECONDS);
                fail("Reservation succeeded after the team's deadline elapsed while waiting for its row lock");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof cn.bugstack.types.exception.AppException);
                assertEquals("E0109", ((cn.bugstack.types.exception.AppException) expected.getCause()).getCode());
            }
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("select lock_count from group_buy_order where team_id=?", Integer.class, team));
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from group_buy_order_list", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test public void persistedPaymentTimeMustRemainInsideValidatedInterval() throws Exception {
        String team = lock("u", "time", null).getTeamId();
        jdbc.update("update group_buy_order_list set create_time=now()-interval 10 minute");
        jdbc.update("update group_buy_order set valid_start_time=now()-interval 10 minute,valid_end_time=now()-interval 1 minute where team_id=?", team);
        Date end = jdbc.queryForObject("select valid_end_time from group_buy_order where team_id=?", Date.class, team);
        Date paid = new Date(end.getTime() - 100);
        settlement.settlementMarketPayOrder(TradePaySuccessEntity.builder().userId("u").source("s01")
                .channel("c01").outTradeNo("time").outTradeTime(paid).build());
        Date stored = jdbc.queryForObject("select out_trade_time from group_buy_order_list", Date.class);
        assertTrue("Stored payment time rounded across the exclusive deadline", stored.before(end));
        assertEquals("Payment fact must retain all supplied milliseconds", paid.getTime(), stored.getTime());
    }

    @Test public void acceptedCallbackLengthMustAlsoFitCompletionTask() throws Exception {
        jdbc.update("update group_buy_activity set target=1");
        String callback = "http://127.0.0.1:1/" + String.join("", java.util.Collections.nCopies(150, "a"));
        MarketPayOrderEntity order = lockService.lockMarketPayOrder(UserEntity.builder().userId("u").build(),
                PayActivityEntity.builder().activityId(900001L).build(),
                PayDiscountEntity.builder().source("s01").channel("c01").goodsId("testsku")
                        .outTradeNo("callback").notifyUrl(callback).build());
        settlement.settlementMarketPayOrder(TradePaySuccessEntity.builder().userId("u").source("s01")
                .channel("c01").outTradeNo("callback").outTradeTime(new Date()).build());
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("select status from group_buy_order where team_id=?", Integer.class, order.getTeamId()));
        assertEquals(callback, jdbc.queryForObject("select notify_url from notify_task", String.class));
    }

    private TradePaySuccessEntity payment(String out, Date time) {
        return TradePaySuccessEntity.builder().userId("u").source("s01").channel("c01").outTradeNo(out).outTradeTime(time).build();
    }

    private void rejected(String code, Callable<?> action) throws Exception {
        try { action.call(); fail("Expected " + code); }
        catch (AppException expected) { assertEquals(code, expected.getCode()); }
    }

    @Test public void callbackBoundariesCompleteAndOversizeIsRejectedBeforeWriting() throws Exception {
        jdbc.update("update group_buy_activity set target=1");
        for (int length : new int[]{128,129,512}) {
            String prefix="http://127.0.0.1:1/";
            String callback=prefix+String.join("",Collections.nCopies(length-prefix.length(),"a"));
            MarketPayOrderEntity order=lockService.lockMarketPayOrder(UserEntity.builder().userId("u").build(),
                    PayActivityEntity.builder().activityId(900001L).build(),
                    PayDiscountEntity.builder().source("s01").channel("c01").goodsId("testsku")
                            .outTradeNo("url"+length).notifyUrl(callback).build());
            settlement.settlementMarketPayOrder(payment("url"+length,new Date()));
            assertEquals(callback,jdbc.queryForObject("select notify_url from notify_task where team_id=?",String.class,order.getTeamId()));
            assertEquals(Integer.valueOf(1),jdbc.queryForObject("select status from group_buy_order_list where order_id=?",Integer.class,order.getOrderId()));
        }
        rejected("0002",()->lockService.lockMarketPayOrder(UserEntity.builder().userId("u").build(),
                PayActivityEntity.builder().activityId(900001L).build(),
                PayDiscountEntity.builder().source("s01").channel("c01").goodsId("testsku").outTradeNo("toolong")
                        .notifyUrl("http://"+String.join("",Collections.nCopies(506,"a"))).build()));
        assertEquals(Integer.valueOf(3),jdbc.queryForObject("select count(*) from group_buy_order_list",Integer.class));
        assertEquals(Integer.valueOf(3),jdbc.queryForObject("select count(*) from group_buy_order where status=1",Integer.class));
        assertEquals(Integer.valueOf(3),jdbc.queryForObject("select count(*) from notify_task",Integer.class));
    }

    @Test public void caseAccentAndTrailingSpacesCannotCrossSettle() throws Exception {
        for (String out : Arrays.asList("CaseOrder","caseorder","Cafe","Caf\u00e9")) {
            MarketPayOrderEntity first=lock("u",out,null);
            assertEquals(first.getOrderId(),lock("u",out,null).getOrderId());
            assertEquals(out,repository.queryOrderByBusinessKey("s01","c01",out).getOutTradeNo());
            assertNull(repository.queryOrderByBusinessKey("s01","c01",out+" "));
        }
        assertEquals(Integer.valueOf(4),jdbc.queryForObject("select count(*) from group_buy_order_list",Integer.class));
        assertEquals(Integer.valueOf(4),jdbc.queryForObject("select count(*) from trade_request_guard",Integer.class));
        rejected("0002",()->settlement.settlementMarketPayOrder(payment("CaseOrder ",new Date())));
        rejected("E0104",()->settlement.settlementMarketPayOrder(payment("CASEORDER",new Date())));
        settlement.settlementMarketPayOrder(payment("caseorder",new Date()));
        settlement.settlementMarketPayOrder(payment("Caf\u00e9",new Date()));
        assertEquals(Integer.valueOf(0),jdbc.queryForObject("select status from group_buy_order_list where out_trade_no='CaseOrder'",Integer.class));
        assertEquals(Integer.valueOf(0),jdbc.queryForObject("select status from group_buy_order_list where out_trade_no='Cafe'",Integer.class));
        assertEquals(Integer.valueOf(2),jdbc.queryForObject("select count(*) from group_buy_order_list where status=1",Integer.class));
    }

    @Test public void waitingAcrossActivityDeadlineAlsoRollsBackReservation() throws Exception {
        String team=lock("leader","leader",null).getTeamId();
        jdbc.update("update group_buy_activity set end_time=now()+interval 2 second");
        ExecutorService pool=Executors.newSingleThreadExecutor();
        final Future<?>[] join=new Future<?>[1];
        try {
            new TransactionTemplate(transactionManager).execute(status->{
                jdbc.queryForObject("select team_id from group_buy_order where team_id=? for update",String.class,team);
                join[0]=pool.submit(()->lock("member","member",team));
                try { Thread.sleep(3500); assertFalse(join[0].isDone()); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return null;
            });
            try { join[0].get(10,TimeUnit.SECONDS); fail("Activity expired while waiting"); }
            catch(ExecutionException expected) { assertTrue(expected.getCause() instanceof AppException); assertEquals("E0102",((AppException)expected.getCause()).getCode()); }
            assertEquals(Integer.valueOf(1),jdbc.queryForObject("select lock_count from group_buy_order where team_id=?",Integer.class,team));
            assertEquals(Integer.valueOf(1),jdbc.queryForObject("select count(*) from group_buy_order_list",Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test public void waitingWithinDeadlineStillAcceptsOneReservation() throws Exception {
        String team=lock("leader","leader",null).getTeamId();
        ExecutorService pool=Executors.newSingleThreadExecutor();
        final Future<?>[] join=new Future<?>[1];
        try {
            new TransactionTemplate(transactionManager).execute(status->{
                jdbc.queryForObject("select team_id from group_buy_order where team_id=? for update",String.class,team);
                jdbc.update("update group_buy_order set valid_end_time=now()+interval 30 second where team_id=?",team);
                join[0]=pool.submit(()->lock("member","member",team));
                try { join[0].get(300,TimeUnit.MILLISECONDS); fail("Expected to wait for row lock"); }
                catch(TimeoutException expected) { } catch(Exception e) { throw new RuntimeException(e); }
                return null;
            });
            assertTrue(join[0].get(10,TimeUnit.SECONDS) instanceof MarketPayOrderEntity);
            assertEquals(Integer.valueOf(2),jdbc.queryForObject("select lock_count from group_buy_order where team_id=?",Integer.class,team));
            assertEquals(Integer.valueOf(2),jdbc.queryForObject("select count(*) from group_buy_order_list",Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test public void millisecondsSurviveBothSqlModesAndRepeatedCallbacks() throws Exception {
        int iteration=0;
        for (boolean truncate : new boolean[]{false,true}) {
            String out="precision"+iteration++;
            String team=lock("u",out,null).getTeamId();
            jdbc.update("update group_buy_order_list set create_time=now()-interval 10 minute where team_id=?",team);
            jdbc.update("update group_buy_order set valid_start_time=now()-interval 10 minute,valid_end_time=now()-interval 1 minute where team_id=?",team);
            Date end=jdbc.queryForObject("select valid_end_time from group_buy_order where team_id=?",Date.class,team);
            Date created=jdbc.queryForObject("select create_time from group_buy_order_list where team_id=?",Date.class,team);
            rejected("E0106",()->settlement.settlementMarketPayOrder(payment(out,end)));
            rejected("E0106",()->settlement.settlementMarketPayOrder(payment(out,new Date(created.getTime()-1))));
            Date paid=new Date(end.getTime()-100);
            new TransactionTemplate(transactionManager).execute(status->{
                String originalMode=jdbc.queryForObject("select @@session.sql_mode",String.class);
                String mode=String.join(",",Arrays.stream(originalMode.split(",")).filter(x->!x.equals("TIME_TRUNCATE_FRACTIONAL")).toArray(String[]::new));
                if(truncate) mode+=",TIME_TRUNCATE_FRACTIONAL";
                jdbc.execute("set session sql_mode='"+mode+"'");
                try { settlement.settlementMarketPayOrder(payment(out,paid)); }
                catch(Exception e) { throw new RuntimeException(e); }
                finally { jdbc.execute("set session sql_mode='"+originalMode+"'"); }
                return null;
            });
            assertEquals(paid.getTime(),jdbc.queryForObject("select out_trade_time from group_buy_order_list where team_id=?",Date.class,team).getTime());
            settlement.settlementMarketPayOrder(payment(out,paid));
            assertEquals(paid.getTime(),jdbc.queryForObject("select out_trade_time from group_buy_order_list where team_id=?",Date.class,team).getTime());
            assertEquals(Integer.valueOf(1),jdbc.queryForObject("select complete_count from group_buy_order where team_id=?",Integer.class,team));
        }
    }
}
