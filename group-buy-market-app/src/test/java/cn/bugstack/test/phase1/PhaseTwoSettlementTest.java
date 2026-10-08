package cn.bugstack.test.phase1;

import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.types.exception.AppException;
import cn.bugstack.infrastructure.dcc.DCCService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Payment facts, count transitions, completion and the committed notification task use real MySQL. */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PhaseOneTradeTest.Config.class)
public class PhaseTwoSettlementTest {
    @Resource private JdbcTemplate jdbc;
    @Resource private ITradeLockOrderService lockService;
    @Resource private ITradeSettlementOrderService settlement;
    @Resource private ITradeRepository repository;
    @Resource private ITradePort port;
    @Resource private DCCService dccService;
    @Resource private PlatformTransactionManager transactionManager;

    @Before public void seed() {
        jdbc.execute("drop trigger if exists phase2_reject_count");
        jdbc.execute("drop trigger if exists phase2_reject_notify");
        PhaseOneTradeTest fixture = new PhaseOneTradeTest();
        ReflectionTestUtils.setField(fixture, "jdbc", jdbc);
        fixture.seed();
        reset(port);
    }

    private MarketPayOrderEntity lock(String user, String out, String team) throws Exception {
        return lockService.lockMarketPayOrder(UserEntity.builder().userId(user).build(),
                PayActivityEntity.builder().activityId(900001L).teamId(team).build(),
                PayDiscountEntity.builder().source("s01").channel("c01").goodsId("testsku")
                        .outTradeNo(out).notifyUrl("http://127.0.0.1:1/never-called").build());
    }

    private TradePaySuccessEntity payment(String user, String out, Date when) {
        return TradePaySuccessEntity.builder().userId(user).source("s01").channel("c01")
                .outTradeNo(out).outTradeTime(when).build();
    }

    private int scalar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private void rejected(String code, Callable<?> action) throws Exception {
        try { action.call(); fail("Expected " + code); }
        catch (AppException expected) { assertEquals(code, expected.getCode()); }
    }

    private List<Object> race(List<Callable<?>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> action : actions) futures.add(pool.submit(() -> {
                ready.countDown(); start.await();
                try { return action.call(); } catch (Exception e) { return e; }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) results.add(future.get(45, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test public void singleMemberCompletesOnceAndReplayReturnsOriginalResult() throws Exception {
        jdbc.update("update group_buy_activity set target=1");
        when(port.groupBuyNotify(any())).thenReturn("success");
        MarketPayOrderEntity order = lock("u", "one", null);
        Date paid = new Date();
        TradePaySettlementEntity first = settlement.settlementMarketPayOrder(payment("u", "one", paid));
        assertEquals(order.getTeamId(), first.getTeamId());
        assertEquals(Long.valueOf(900001), first.getActivityId());
        assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?", order.getTeamId()));
        assertEquals(1, scalar("select status from group_buy_order where team_id=?", order.getTeamId()));
        assertEquals(1, scalar("select count(*) from notify_task where team_id=?", order.getTeamId()));
        assertEquals("one", com.alibaba.fastjson.JSON.parseObject(jdbc.queryForObject(
                "select parameter_json from notify_task where team_id=?", String.class, order.getTeamId()))
                .getJSONArray("outTradeNoList").getString(0));
        jdbc.update("update group_buy_order set valid_end_time=now()-interval 1 minute where team_id=?", order.getTeamId());
        TradePaySettlementEntity replay = settlement.settlementMarketPayOrder(payment("u", "one", paid));
        assertEquals(first, replay);
        assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?", order.getTeamId()));
        assertEquals(1, scalar("select notify_count from notify_task where team_id=?", order.getTeamId()));
        verify(port, times(1)).groupBuyNotify(any());
        rejected("E0113", () -> settlement.settlementMarketPayOrder(payment("u", "one", new Date(paid.getTime() - 1))));
        rejected("E0104", () -> settlement.settlementMarketPayOrder(payment("u", "missing", paid)));
    }

    @Test public void samePaymentStillSucceedsAfterChannelIsBlacklisted() throws Exception {
        verifyReplayAfterBlacklist(false);
    }

    @Test public void conflictingPaymentStillReturnsConflictAfterChannelIsBlacklisted() throws Exception {
        verifyReplayAfterBlacklist(true);
    }

    private void verifyReplayAfterBlacklist(boolean conflicting) throws Exception {
        jdbc.update("update group_buy_activity set target=1");
        when(port.groupBuyNotify(any())).thenReturn("success");
        MarketPayOrderEntity order = lock("u", "blackpaid", null);
        MarketPayOrderEntity pending = lock("u2", "blackpending", null);
        Date paid = new Date();
        TradePaySettlementEntity first = settlement.settlementMarketPayOrder(payment("u", "blackpaid", paid));
        Object oldBlacklist = ReflectionTestUtils.getField(dccService, "scBlacklist");
        try {
            ReflectionTestUtils.setField(dccService, "scBlacklist", "s01c01");
            if (conflicting) {
                rejected("E0113", () -> settlement.settlementMarketPayOrder(payment("u", "blackpaid", new Date(paid.getTime()-1))));
            } else {
                assertEquals(first, settlement.settlementMarketPayOrder(payment("u", "blackpaid", paid)));
            }
            rejected("E0112", () -> settlement.settlementMarketPayOrder(payment("other", "blackpaid", paid)));
            rejected("E0105", () -> settlement.settlementMarketPayOrder(payment("u2", "blackpending", paid)));
            assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?", order.getTeamId()));
            assertEquals(0, scalar("select complete_count from group_buy_order where team_id=?", pending.getTeamId()));
            assertEquals(0, scalar("select status from group_buy_order_list where order_id=?", pending.getOrderId()));
            assertEquals(paid.getTime(), jdbc.queryForObject("select out_trade_time from group_buy_order_list where order_id=?", Date.class, order.getOrderId()).getTime());
            assertEquals(1, scalar("select count(*) from notify_task"));
            verify(port, times(1)).groupBuyNotify(any());
        } finally {
            ReflectionTestUtils.setField(dccService, "scBlacklist", oldBlacklist);
        }
    }

    @Test public void thirtyConcurrentIdenticalPaymentsCountOnce() throws Exception {
        MarketPayOrderEntity order = lock("u", "same", null);
        Date paid = new Date();
        List<Callable<?>> actions = new ArrayList<>();
        for (int i=0;i<30;i++) actions.add(() -> settlement.settlementMarketPayOrder(payment("u", "same", paid)));
        List<Object> results = race(actions);
        for (Object result : results) {
            assertTrue(String.valueOf(result), result instanceof TradePaySettlementEntity);
            assertEquals(order.getTeamId(), ((TradePaySettlementEntity) result).getTeamId());
        }
        assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?", order.getTeamId()));
        assertEquals(1, scalar("select count(*) from group_buy_order_list where status=1 and team_id=?", order.getTeamId()));
        assertEquals(0, scalar("select count(*) from notify_task"));
        assertEquals(paid.getTime(), jdbc.queryForObject("select out_trade_time from group_buy_order_list", Date.class).getTime());
    }

    @Test public void competingPaymentFactsChooseOneWithoutChangingItOnRetry() throws Exception {
        MarketPayOrderEntity order = lock("u", "facts", null);
        jdbc.update("update group_buy_order_list set create_time=now()-interval 1 minute");
        jdbc.update("update group_buy_order set valid_start_time=now()-interval 1 minute");
        Date a = new Date(System.currentTimeMillis()-1000);
        Date b = new Date(a.getTime()+1);
        List<Callable<?>> actions = new ArrayList<>();
        for (int i=0;i<20;i++) { final Date value = i % 2 == 0 ? a : b; actions.add(() -> settlement.settlementMarketPayOrder(payment("u", "facts", value))); }
        List<Object> results = race(actions);
        Date persisted = jdbc.queryForObject("select out_trade_time from group_buy_order_list", Date.class);
        assertTrue(persisted.getTime() == a.getTime() || persisted.getTime() == b.getTime());
        int success=0, conflict=0;
        for (Object result : results) {
            if (result instanceof TradePaySettlementEntity) success++;
            else { assertTrue(String.valueOf(result), result instanceof AppException); assertEquals("E0113", ((AppException) result).getCode()); conflict++; }
        }
        assertEquals(10, success); assertEquals(10, conflict);
        assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?", order.getTeamId()));
        assertEquals(persisted.getTime(), jdbc.queryForObject("select out_trade_time from group_buy_order_list", Date.class).getTime());
    }

    private void waitForTeamWaiters(int count) throws Exception {
        waitForWaiters("group_buy_order", count);
    }

    private void waitForWaiters(String table, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            int waiting = scalar("select count(*) from performance_schema.data_lock_waits w " +
                    "join performance_schema.data_locks l on w.requesting_engine_lock_id=l.engine_lock_id " +
                    "where l.object_schema='group_buy_market_phase1_test' and l.object_name=?", table);
            if (waiting >= count) return;
            Thread.sleep(30);
        }
        fail("Settlements did not reach the expected lock barrier: " + table);
    }

    @Test public void waitingReplaysUseCommittedFactAfterBlacklistChanges() throws Exception {
        String team = lock("u", "waitpolicy", null).getTeamId();
        Date paid = new Date();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        Future<?>[] calls = new Future<?>[3];
        Object oldBlacklist = ReflectionTestUtils.getField(dccService, "scBlacklist");
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select team_id from group_buy_order where team_id=? for update", String.class, team);
                calls[0] = pool.submit(() -> settlement.settlementMarketPayOrder(payment("u", "waitpolicy", paid)));
                try {
                    waitForTeamWaiters(1);
                    calls[1] = pool.submit(() -> settlement.settlementMarketPayOrder(payment("u", "waitpolicy", paid)));
                    calls[2] = pool.submit(() -> {
                        rejected("E0113", () -> settlement.settlementMarketPayOrder(payment("u", "waitpolicy", new Date(paid.getTime()+1))));
                        return null;
                    });
                    waitForWaiters("group_buy_order_list", 2);
                    ReflectionTestUtils.setField(dccService, "scBlacklist", "s01c01");
                } catch (Exception e) { throw new RuntimeException(e); }
                return null;
            });
            assertEquals(calls[0].get(15, TimeUnit.SECONDS), calls[1].get(15, TimeUnit.SECONDS));
            calls[2].get(15, TimeUnit.SECONDS);
            assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?", team));
            assertEquals(paid.getTime(), jdbc.queryForObject("select out_trade_time from group_buy_order_list", Date.class).getTime());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(15, TimeUnit.SECONDS);
            ReflectionTestUtils.setField(dccService, "scBlacklist", oldBlacklist);
        }
    }

    @Test public void lastTwoConcurrentPaymentsCompleteOneTeamWithWholeRoster() throws Exception {
        String team = lock("u1", "pay1", null).getTeamId();
        lock("u2", "pay2", team);
        lock("u3", "pay3", team);
        settlement.settlementMarketPayOrder(payment("u1", "pay1", new Date()));
        assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?", team));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        @SuppressWarnings("unchecked") Future<TradePaySettlementEntity>[] payments = new Future[2];
        Date t2 = new Date(), t3 = new Date();
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select team_id from group_buy_order where team_id=? for update", String.class, team);
                payments[0] = pool.submit(() -> settlement.settlementMarketPayOrder(payment("u2", "pay2", t2)));
                payments[1] = pool.submit(() -> settlement.settlementMarketPayOrder(payment("u3", "pay3", t3)));
                try { waitForTeamWaiters(2); assertFalse(payments[0].isDone()); assertFalse(payments[1].isDone()); }
                catch (Exception e) { throw new RuntimeException(e); }
                return null;
            });
            assertEquals(team, payments[0].get(15, TimeUnit.SECONDS).getTeamId());
            assertEquals(team, payments[1].get(15, TimeUnit.SECONDS).getTeamId());
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(3, scalar("select complete_count from group_buy_order where team_id=?", team));
        assertEquals(1, scalar("select status from group_buy_order where team_id=?", team));
        assertEquals(1, scalar("select count(*) from notify_task where team_id=?", team));
        String payload = jdbc.queryForObject("select parameter_json from notify_task where team_id=?", String.class, team);
        List<String> roster = com.alibaba.fastjson.JSON.parseObject(payload).getJSONArray("outTradeNoList").toJavaList(String.class);
        assertEquals(new HashSet<>(Arrays.asList("pay1","pay2","pay3")), new HashSet<>(roster));
        assertEquals(3, roster.size());
        assertEquals(3, scalar("select count(*) from group_buy_order_list where team_id=? and status=1", team));
    }

    @Test public void failedPaymentCountUpdateRollsBackTheDetailAndRetryCanCommit() throws Exception {
        MarketPayOrderEntity order = lock("u", "rollback1", null);
        jdbc.execute("create trigger phase2_reject_count before update on group_buy_order for each row " +
                "begin if new.complete_count > old.complete_count then signal sqlstate '45000' set message_text='injected count failure'; end if; end");
        try {
            try { settlement.settlementMarketPayOrder(payment("u", "rollback1", new Date())); fail(); }
            catch (Exception expected) { /* The injected database failure must abort the transaction. */ }
            assertEquals(0, scalar("select status from group_buy_order_list where order_id=?", order.getOrderId()));
            assertEquals(0, scalar("select complete_count from group_buy_order where team_id=?", order.getTeamId()));
            assertEquals(0, scalar("select count(*) from notify_task"));
        } finally { jdbc.execute("drop trigger if exists phase2_reject_count"); }
        settlement.settlementMarketPayOrder(payment("u", "rollback1", new Date()));
        assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?", order.getTeamId()));
    }

    @Test public void failedNotificationInsertRollsBackTeamAndPaymentTogether() throws Exception {
        jdbc.update("update group_buy_activity set target=1");
        MarketPayOrderEntity order = lock("u", "rollback2", null);
        jdbc.execute("create trigger phase2_reject_notify before insert on notify_task for each row " +
                "signal sqlstate '45000' set message_text='injected notify insert failure'");
        try {
            try { settlement.settlementMarketPayOrder(payment("u", "rollback2", new Date())); fail(); }
            catch (Exception expected) { /* The injected database failure must abort the transaction. */ }
            assertEquals(0, scalar("select status from group_buy_order_list where order_id=?", order.getOrderId()));
            assertEquals(0, scalar("select complete_count from group_buy_order where team_id=?", order.getTeamId()));
            assertEquals(0, scalar("select status from group_buy_order where team_id=?", order.getTeamId()));
            assertEquals(0, scalar("select count(*) from notify_task"));
        } finally { jdbc.execute("drop trigger if exists phase2_reject_notify"); }
        settlement.settlementMarketPayOrder(payment("u", "rollback2", new Date()));
        assertEquals(1, scalar("select count(*) from notify_task where team_id=?", order.getTeamId()));
    }

    private ITradeRepository failingNotificationRepository(String methodName) {
        return (ITradeRepository) Proxy.newProxyInstance(ITradeRepository.class.getClassLoader(),
                new Class<?>[]{ITradeRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals(methodName) && (args == null || args.length == 0 || args.length == 1)) {
                        throw new IllegalStateException("injected post-commit failure: " + methodName);
                    }
                    try { return method.invoke(repository, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
    }

    @Test public void postCommitNotificationQuerySendOrUpdateFailureKeepsSettlementSuccess() throws Exception {
        int n=0;
        for (String failure : Arrays.asList("queryUnExecutedNotifyTaskList", "groupBuyNotify", "updateNotifyTaskStatusSuccess")) {
            jdbc.update("update group_buy_activity set target=1");
            String out="notify"+(++n);
            MarketPayOrderEntity order=lock("u", out, null);
            Date paid=new Date();
            reset(port);
            when(port.groupBuyNotify(any())).thenReturn("success");
            if (failure.equals("groupBuyNotify")) when(port.groupBuyNotify(any())).thenThrow(new IllegalStateException("injected send failure"));
            if (!failure.equals("groupBuyNotify")) ReflectionTestUtils.setField(settlement, "repository", failingNotificationRepository(failure));
            try {
                TradePaySettlementEntity result=settlement.settlementMarketPayOrder(payment("u", out, paid));
                assertEquals(order.getTeamId(),result.getTeamId());
            } finally { ReflectionTestUtils.setField(settlement, "repository", repository); }
            assertEquals(1, scalar("select status from group_buy_order_list where order_id=?",order.getOrderId()));
            assertEquals(1, scalar("select complete_count from group_buy_order where team_id=?",order.getTeamId()));
            assertEquals(1, scalar("select status from group_buy_order where team_id=?",order.getTeamId()));
            int expectedNotifyStatus = failure.equals("queryUnExecutedNotifyTaskList") ? 0 :
                    failure.equals("groupBuyNotify") ? 2 : 4;
            assertEquals(expectedNotifyStatus, scalar("select notify_status from notify_task where team_id=?",order.getTeamId()));
            TradePaySettlementEntity replay=settlement.settlementMarketPayOrder(payment("u",out,paid));
            assertEquals(order.getTeamId(),replay.getTeamId());
        }
    }
}
