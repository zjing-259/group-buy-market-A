package cn.bugstack.test.v2;

import cn.bugstack.api.v2.EventProcessingResult;
import cn.bugstack.api.v2.ReconciliationCompleteRequest;
import cn.bugstack.api.v2.ReconciliationDecision;
import cn.bugstack.api.v2.TradeQueryRequest;
import cn.bugstack.api.v2.CloseRequest;
import cn.bugstack.api.v2.ReserveRequest;
import cn.bugstack.api.v2.Offer;
import cn.bugstack.api.v2.TrialRequest;
import cn.bugstack.api.v2.Reservation;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.v2.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import java.util.Date;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes=V2PaymentFlowTest.Config.class)
public class V2PaymentFlowTest {
    @Configuration @EnableTransactionManagement
    @Import({V2EventEngine.class,V2PaymentEngine.class,V2TeamEngine.class,V2TradeEngine.class,V2OutboxService.class,V2PaymentIdentity.class})
    public static class Config {
        @Bean(destroyMethod="close") public HikariDataSource dataSource() {
            return V2TestDatabase.dataSource();
        }
        @Bean public JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean public PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean public FactoryBean<V2MarketingEngine> marketingEngine() {
            return new FactoryBean<V2MarketingEngine>() {
                private final V2MarketingEngine instance=mock(V2MarketingEngine.class);
                @Override public V2MarketingEngine getObject() { return instance; }
                @Override public Class<?> getObjectType() { return V2MarketingEngine.class; }
                @Override public boolean isSingleton() { return true; }
            };
        }
    }
    @Resource private JdbcTemplate jdbc;
    @Resource private V2EventEngine events;
    @Resource private V2TeamEngine teams;
    @Resource private V2TradeEngine trades;
    @Resource private V2MarketingEngine marketing;
    @Resource private DataSource dataSource;
    private String legacyBusinessId;

    @After public void removeLegacyFixture() {
        if (legacyBusinessId!=null) jdbc.update("delete from group_buy_order_list where biz_id=?",legacyBusinessId);
    }

    @Before public void seed() {
        org.mockito.Mockito.reset(marketing);
        V2TestDatabase.assertIsolated(jdbc);
        for (String table:Arrays.asList("ab_outbox","ab_waiting_dependency","ab_inbox","ab_exception_payment",
                "ab_payment_guard","ab_trade_guard","ab_trade_order","ab_team")) jdbc.update("delete from "+table);
        jdbc.update("insert into ab_team(team_id,source,channel,activity_id,goods_id,target_count,reserved_count,paid_count,"+
                "team_status,team_version,valid_start_time,valid_end_time) values('team-pay','s01','c01',1,'goods-pay',"+
                "2,2,0,'FORMING',1,now()-interval 2 minute,now()+interval 10 minute)");
        for (int i=1;i<=2;i++) jdbc.update("insert into ab_trade_order(source,channel,out_trade_no,record_kind,order_id,"+
                "user_id,goods_id,activity_id,product_version,quantity,team_id,original_price,deduction_price,pay_price,"+
                "currency,reserved_at,payment_deadline,order_status,record_version) values('s01','c01',?,'RESERVATION',"+
                "?,?,'goods-pay',1,1,1,'team-pay',100.00,20.00,80.00,'CNY',now()-interval 1 minute,"+
                "now()+interval 10 minute,'RESERVED',1)","order-pay-"+i,"a-order-pay-"+i,"user-pay-"+i);
    }

    private String payment(String eventId,int member,String paidAt) {
        return "{\"eventId\":\""+eventId+"\",\"eventType\":\"PAYMENT_SUCCEEDED\",\"schemaVersion\":1,"+
                "\"producer\":\"zj-market\",\"occurredAt\":\""+paidAt+"\",\"source\":\"s01\","+
                "\"channel\":\"c01\",\"payload\":{\"userId\":\"user-pay-"+member+"\","+
                "\"outTradeNo\":\"order-pay-"+member+"\",\"paymentId\":\"payment-pay-"+member+"\","+
                "\"paidAmount\":\"80.00\",\"currency\":\"CNY\",\"paidAt\":\""+paidAt+"\"}}";
    }

    @Test public void twoPaymentsCompleteOnceAndConflictingRetryCannotChangeFact() {
        Timestamp now=jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class);
        String paidAt=V2Format.utc(now);
        String first=payment("event-pay-1",1,paidAt);
        assertEquals("APPLIED",events.process(first,"zj-market").getOutcome());
        assertEquals("APPLIED",events.process(payment("event-pay-2",2,paidAt),"zj-market").getOutcome());
        assertEquals("COMPLETED",jdbc.queryForObject("select team_status from ab_team where team_id='team-pay'",String.class));
        assertEquals(2,jdbc.queryForObject("select paid_count from ab_team where team_id='team-pay'",Integer.class).intValue());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_outbox where event_type='TEAM_COMPLETED'",Long.class).longValue());
        assertEquals("APPLIED",events.process(first,"zj-market").getOutcome());
        assertEquals("IGNORED",events.process(payment("event-pay-3",1,paidAt),"zj-market").getOutcome());
        String changed=V2Format.utc(new Timestamp(now.getTime()+1000));
        EventProcessingResult rejected=events.process(payment("event-pay-4",1,changed),"zj-market");
        assertEquals("REJECTED",rejected.getOutcome());
        assertEquals("E0113",rejected.getCode());
        assertEquals(2,jdbc.queryForObject("select paid_count from ab_team where team_id='team-pay'",Integer.class).intValue());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_outbox where event_type='TEAM_COMPLETED'",Long.class).longValue());
    }

    private ReconciliationDecision closed(int member) {
        ReconciliationDecision d=new ReconciliationDecision();
        d.setUserId("user-pay-"+member); d.setOutTradeNo("order-pay-"+member);
        d.setDisposition("CLOSED_UNPAID"); d.setCloseId("close-pay-"+member); return d;
    }

    @Test public void reconciliationRetryIgnoresListOrderButRejectsChangedFacts() {
        jdbc.update("update ab_team set team_status='RECONCILING',team_version=2,reserved_count=0,"+
                "reconciliation_id='reconcile1',roster_version='roster1',roster_json='[\"order-pay-1\",\"order-pay-2\"]' " +
                "where team_id='team-pay'");
        for (int i=1;i<=2;i++) jdbc.update("update ab_trade_order set order_status='CANCELLED',close_id=?,"+
                "closed_at=now(3),record_version=2 where out_trade_no=?","close-pay-"+i,"order-pay-"+i);
        ReconciliationCompleteRequest request=new ReconciliationCompleteRequest();
        request.setSource("s01"); request.setChannel("c01"); request.setTeamId("team-pay");
        request.setReconciliationId("reconcile1"); request.setRosterVersion("roster1");
        request.setCompletionId("completion1"); request.setCheckedAt(V2Format.now());
        request.setResults(Arrays.asList(closed(1),closed(2)));
        assertEquals("FAILED",teams.complete(request).getTeamStatus());
        request.setResults(Arrays.asList(closed(2),closed(1)));
        assertEquals("FAILED",teams.complete(request).getTeamStatus());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_outbox where event_type='TEAM_FAILED'",Long.class).longValue());
        request.setCheckedAt(V2Format.utc(new Timestamp(System.currentTimeMillis()+1000)));
        try { teams.complete(request); org.junit.Assert.fail("changed completion must conflict"); }
        catch (V2Failure expected) { assertEquals("RECONCILIATION_CONFLICT",expected.getCode()); }
    }

    @Test public void v2CannotReserveOrCloseAnExistingV1BusinessKey() {
        String number="lv2"+UUID.randomUUID().toString().substring(0,8);
        legacyBusinessId=UUID.randomUUID().toString();
        jdbc.update("insert into group_buy_order_list(user_id,team_id,order_id,activity_id,start_time,end_time,"+
                "goods_id,source,channel,original_price,deduction_price,status,out_trade_no,biz_id) " +
                "values('legacy-user','teamold','orderold0001',1,now()-interval 1 day,now()+interval 1 day,"+
                "'old-goods','s01','c01',100.00,20.00,0,?,?)",number,legacyBusinessId);
        TradeQueryRequest query=new TradeQueryRequest(); query.setSource("s01"); query.setChannel("c01");
        query.setUserId("legacy-user"); query.setOutTradeNo(number);
        try { trades.query(query); org.junit.Assert.fail("v1 facts must remain explicit"); }
        catch (V2Failure expected) { assertEquals("LEGACY_FACT_UNVERIFIABLE",expected.getCode()); }
        CloseRequest close=new CloseRequest(); close.setSource("s01"); close.setChannel("c01");
        close.setUserId("legacy-user"); close.setOutTradeNo(number); close.setCloseId("close-legacy");
        close.setReason("ORDER_ABORT"); close.setPaymentDisposition("NEVER_OPENED"); close.setVerifiedAt(V2Format.now());
        try { trades.close(close); org.junit.Assert.fail("v2 close must not mask a v1 order"); }
        catch (V2Failure expected) { assertEquals("LEGACY_FACT_UNVERIFIABLE",expected.getCode()); }
        ReserveRequest reserve=new ReserveRequest(); reserve.setSource("s01"); reserve.setChannel("c01");
        reserve.setUserId("legacy-user"); reserve.setOutTradeNo(number); reserve.setGoodsId("old-goods");
        reserve.setActivityId("1"); reserve.setProductVersion("1"); reserve.setQuantity(1);
        reserve.setExpectedPayPrice("80.00"); reserve.setCurrency("CNY");
        try { trades.reserve(reserve); org.junit.Assert.fail("v2 reserve must not duplicate a v1 order"); }
        catch (V2Failure expected) { assertEquals("LEGACY_FACT_UNVERIFIABLE",expected.getCode()); }
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_trade_order where out_trade_no=?",Long.class,number).longValue());
    }

    @Test public void joinWaitsForTeamLockAndRechecksExpiry() throws Exception {
        jdbc.update("update ab_team set target_count=3,valid_end_time=now(3)+interval 2 second where team_id='team-pay'");
        Offer offer=new Offer(); offer.setOriginalPrice("100.00"); offer.setDeductionPrice("20.00");
        offer.setPayPrice("80.00");
        V2MarketingEngine.Quote quote=new V2MarketingEngine.Quote();
        quote.eligible=true; quote.offer=offer;
        quote.activity=GroupBuyActivityDiscountVO.builder().activityId(1L).target(3).validTime(10)
                .takeLimitCount(10).startTime(new Date(System.currentTimeMillis()-60000))
                .endTime(new Date(System.currentTimeMillis()+60000)).build();
        CountDownLatch quoted=new CountDownLatch(1);
        when(marketing.quote(any(TrialRequest.class),eq(true))).thenAnswer(call->{ quoted.countDown(); return quote; });
        ReserveRequest reserve=new ReserveRequest(); reserve.setSource("s01"); reserve.setChannel("c01");
        reserve.setUserId("user-join"); reserve.setOutTradeNo("join-expired"); reserve.setGoodsId("goods-pay");
        reserve.setActivityId("1"); reserve.setProductVersion("1"); reserve.setQuantity(1);
        reserve.setExpectedPayPrice("80.00"); reserve.setCurrency("CNY"); reserve.setTeamId("team-pay");
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try (Connection lock=dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (Statement statement=lock.createStatement()) {
                statement.executeQuery("select team_id from ab_team where team_id='team-pay' for update").close();
            }
            Future<?> joining=pool.submit(()->trades.reserve(reserve));
            try {
                org.junit.Assert.assertTrue("join did not reach quote",quoted.await(2,TimeUnit.SECONDS));
                Thread.sleep(2300);
            } finally { lock.commit(); }
            try { joining.get(5,TimeUnit.SECONDS); org.junit.Assert.fail("expired team accepted a join"); }
            catch (ExecutionException rejected) {
                org.junit.Assert.assertTrue(rejected.getCause() instanceof V2Failure);
                assertEquals("E0109",((V2Failure)rejected.getCause()).getCode());
            }
        } finally { pool.shutdownNow(); }
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_trade_order where out_trade_no='join-expired'",Long.class).longValue());
        assertEquals(2,jdbc.queryForObject("select reserved_count from ab_team where team_id='team-pay'",Integer.class).intValue());
    }

    @Test public void paymentWaitsForTeamLockAndStartsReconciliationAfterExpiry() throws Exception {
        jdbc.update("update ab_team set valid_end_time=now(3)+interval 2 second where team_id='team-pay'");
        jdbc.update("update ab_trade_order set payment_deadline=now(3)+interval 2 second where team_id='team-pay'");
        String paidAt=V2Format.utc(jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class));
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try (Connection lock=dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (Statement statement=lock.createStatement()) {
                statement.executeQuery("select team_id from ab_team where team_id='team-pay' for update").close();
            }
            Future<EventProcessingResult> settling=pool.submit(()->events.process(payment("pay-after-wait",1,paidAt),"zj-market"));
            try { Thread.sleep(2300); }
            finally { lock.commit(); }
            assertEquals("APPLIED",settling.get(5,TimeUnit.SECONDS).getOutcome());
        } finally { pool.shutdownNow(); }
        assertEquals("RECONCILING",jdbc.queryForObject("select team_status from ab_team where team_id='team-pay'",String.class));
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_outbox where event_type='TEAM_RECONCILIATION_REQUIRED'",Long.class).longValue());
    }

    private CloseRequest closeRequest(int member) {
        CloseRequest r=new CloseRequest(); r.setSource("s01"); r.setChannel("c01");
        r.setUserId("user-pay-"+member); r.setOutTradeNo("order-pay-"+member);
        r.setCloseId("close-pay-"+member); r.setReason("USER_CANCEL");
        r.setPaymentDisposition("CLOSED_UNPAID"); r.setVerifiedAt(V2Format.now()); return r;
    }

    private ReconciliationCompleteRequest reconciliation(ReconciliationDecision... decisions) {
        cn.bugstack.api.v2.ReconciliationQueryRequest query=new cn.bugstack.api.v2.ReconciliationQueryRequest();
        query.setSource("s01"); query.setChannel("c01"); query.setTeamId("team-pay");
        cn.bugstack.api.v2.ReconciliationSnapshot snapshot=teams.queryReconciliation(query);
        ReconciliationCompleteRequest r=new ReconciliationCompleteRequest(); r.setSource("s01"); r.setChannel("c01");
        r.setTeamId("team-pay"); r.setReconciliationId(snapshot.getReconciliationId());
        r.setRosterVersion(snapshot.getRosterVersion()); r.setCompletionId("complete-pay");
        r.setCheckedAt(V2Format.now()); r.setResults(Arrays.asList(decisions)); return r;
    }

    private void expired() {
        jdbc.update("update ab_team set valid_end_time=now(3)-interval 20 second where team_id='team-pay'");
        jdbc.update("update ab_trade_order set payment_deadline=now(3)-interval 20 second where team_id='team-pay'");
        teams.expire("team-pay");
    }

    private ReconciliationDecision outside(int member,String paidAt) {
        ReconciliationDecision d=new ReconciliationDecision(); d.setUserId("user-pay-"+member);
        d.setOutTradeNo("order-pay-"+member); d.setDisposition("PAID_OUTSIDE_WINDOW");
        d.setPaymentId("late-pay-"+member); d.setPaidAt(paidAt); d.setPaidAmount("80.00");
        d.setCurrency("CNY"); d.setRefundId("refund-pay-"+member); return d;
    }

    private String refund(String eventId,int member,String version,String status,String paidAt) {
        String extra=paidAt==null?"":",\"paidAt\":\""+paidAt+"\",\"paidAmount\":\"80.00\"";
        return "{\"eventId\":\""+eventId+"\",\"eventType\":\"REFUND_STATUS_CHANGED\",\"schemaVersion\":1,"+
                "\"producer\":\"zj-market\",\"occurredAt\":\""+V2Format.now()+"\",\"source\":\"s01\",\"channel\":\"c01\","+
                "\"payload\":{\"userId\":\"user-pay-"+member+"\",\"outTradeNo\":\"order-pay-"+member+"\","+
                "\"paymentId\":\""+(paidAt==null?"payment-pay-":"late-pay-")+member+"\",\"refundId\":\"refund-pay-"+member+"\","+
                "\"refundAmount\":\"80.00\",\"currency\":\"CNY\",\"refundStatus\":\""+status+"\",\"refundVersion\":\""+version+"\","+
                "\"occurredAt\":\""+V2Format.now()+"\""+extra+"}}";
    }

    @Test public void rejectedPaymentLeavesOnlyRejectionAndNoReconciliationSideEffects() {
        jdbc.update("update ab_team set valid_end_time=now(3)-interval 1 second where team_id='team-pay'");
        jdbc.update("insert into ab_payment_guard values('s01','c01','payment-pay-1','other-order')");
        String paidAt=V2Format.utc(jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class));
        EventProcessingResult result=events.process(payment("rejected-identity",1,paidAt),"zj-market");
        assertEquals("PAYMENT_ID_CONFLICT",result.getCode());
        assertEquals("FORMING",jdbc.queryForObject("select team_status from ab_team where team_id='team-pay'",String.class));
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_outbox where event_type='TEAM_RECONCILIATION_REQUIRED'",Long.class).longValue());
        assertEquals("RESERVED",jdbc.queryForObject("select order_status from ab_trade_order where out_trade_no='order-pay-1'",String.class));
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_outbox",Long.class).longValue());
    }

    @Test public void paymentBeforeReservationWaitsThenSameEventRecovers() {
        jdbc.update("update ab_trade_order set out_trade_no='temporarily-hidden' where out_trade_no='order-pay-1'");
        String raw=payment("wait-for-order",1,V2Format.utc(jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class)));
        assertEquals("WAITING_DEPENDENCY",events.process(raw,"zj-market").getProcessingStatus());
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_outbox",Long.class).longValue());
        jdbc.update("update ab_trade_order set out_trade_no='order-pay-1' where out_trade_no='temporarily-hidden'");
        assertEquals("APPLIED",events.process(raw,"zj-market").getOutcome());
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_waiting_dependency",Long.class).longValue());
    }

    @Test public void closeMarkerIsPersistentAndCannotBeReusedByAnotherUser() {
        CloseRequest r=closeRequest(1); r.setOutTradeNo("marker-only"); r.setPaymentDisposition("NEVER_OPENED");
        trades.close(r); trades.close(r);
        TradeQueryRequest q=new TradeQueryRequest(); q.setSource("s01"); q.setChannel("c01");
        q.setUserId(r.getUserId()); q.setOutTradeNo(r.getOutTradeNo());
        assertEquals("CLOSE_MARKER",trades.query(q).getRecordKind());
        r.setReason("ORDER_ABORT");
        try { trades.close(r); org.junit.Assert.fail("changed close fact accepted"); }
        catch (V2Failure expected) { assertEquals("CLOSE_CONFLICT",expected.getCode()); }
        q.setUserId("User-pay-1");
        try { trades.query(q); org.junit.Assert.fail("case-insensitive owner accepted"); }
        catch (V2Failure expected) { assertEquals("FORBIDDEN",expected.getCode()); }
    }

    @Test public void concurrentCloseAndPaymentHaveExactlyOneWinner() throws Exception {
        String raw=payment("pay-close-race",1,V2Format.utc(jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class)));
        CloseRequest close=closeRequest(1); CountDownLatch start=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<String> closing=pool.submit(()->{start.await(); try {trades.close(close); return "CANCELLED";}
                catch (V2Failure e) {return e.getCode();}});
            Future<EventProcessingResult> paying=pool.submit(()->{start.await(); return events.process(raw,"zj-market");});
            start.countDown(); String closed=closing.get(10,TimeUnit.SECONDS);
            EventProcessingResult paid=paying.get(10,TimeUnit.SECONDS);
            if ("CANCELLED".equals(closed)) {
                assertEquals("ORDER_CLOSED",paid.getCode());
                assertEquals(0,jdbc.queryForObject("select paid_count from ab_team where team_id='team-pay'",Integer.class).intValue());
                assertEquals(1,jdbc.queryForObject("select reserved_count from ab_team where team_id='team-pay'",Integer.class).intValue());
            } else {
                assertEquals("ORDER_ALREADY_PAID",closed); assertEquals("APPLIED",paid.getOutcome());
                assertEquals(1,jdbc.queryForObject("select paid_count from ab_team where team_id='team-pay'",Integer.class).intValue());
                assertEquals(2,jdbc.queryForObject("select reserved_count from ab_team where team_id='team-pay'",Integer.class).intValue());
            }
        } finally {pool.shutdownNow();}
    }

    @Test public void failedTeamRefundWaitsForEvidenceThenVersionsRemainMonotonic() {
        String paidAt=V2Format.utc(jdbc.queryForObject("select now(3)-interval 30 second",Timestamp.class));
        assertEquals("APPLIED",events.process(payment("paid-before-failure",1,paidAt),"zj-market").getOutcome());
        String waiting=refund("refund-waiting",1,"1","PENDING",null);
        assertEquals("WAITING_DEPENDENCY",events.process(waiting,"zj-market").getProcessingStatus());
        expired(); trades.close(closeRequest(2));
        ReconciliationDecision paid=new ReconciliationDecision(); paid.setUserId("user-pay-1");
        paid.setOutTradeNo("order-pay-1"); paid.setDisposition("PAID_ACCEPTED"); paid.setPaymentId("payment-pay-1");
        assertEquals("FAILED",teams.complete(reconciliation(paid,closed(2))).getTeamStatus());
        assertEquals("APPLIED",events.process(waiting,"zj-market").getOutcome());
        assertEquals("APPLIED",events.process(refund("refund-success",1,"3","SUCCEEDED",null),"zj-market").getOutcome());
        assertEquals("IGNORED",events.process(refund("refund-old",1,"2","FAILED",null),"zj-market").getOutcome());
        assertEquals("REFUND_CONFLICT",events.process(refund("refund-regression",1,"4","FAILED",null),"zj-market").getCode());
        assertEquals("APPLIED",events.process(refund("refund-new-success",1,"4","SUCCEEDED",null),"zj-market").getOutcome());
        assertEquals("SUCCEEDED",jdbc.queryForObject("select refund_status from ab_trade_order where out_trade_no='order-pay-1'",String.class));
    }

    @Test public void reconciliationRollsBackEarlierMemberWhenLaterMemberIsUnconfirmed() {
        expired(); String paidAt=V2Format.utc(jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class));
        try {teams.complete(reconciliation(outside(1,paidAt),closed(2))); org.junit.Assert.fail("partial completion accepted");}
        catch (V2Failure expected) {assertEquals("RECONCILIATION_INCOMPLETE",expected.getCode());}
        assertEquals(2,jdbc.queryForObject("select reserved_count from ab_team where team_id='team-pay'",Integer.class).intValue());
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_exception_payment",Long.class).longValue());
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_payment_guard",Long.class).longValue());
        assertEquals("RECONCILING",jdbc.queryForObject("select team_status from ab_team where team_id='team-pay'",String.class));
    }

    @Test public void existingLateRefundEvidenceCannotBeChangedByReconciliation() {
        expired(); trades.close(closeRequest(1)); trades.close(closeRequest(2));
        String paidAt=V2Format.utc(jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class));
        assertEquals("APPLIED",events.process(refund("late-refund-evidence",1,"1","PENDING",paidAt),"zj-market").getOutcome());
        ReconciliationDecision d=outside(1,paidAt); d.setRefundId("another-refund");
        try {teams.complete(reconciliation(d,closed(2))); org.junit.Assert.fail("changed exceptional payment accepted");}
        catch (V2Failure expected) {assertEquals("RECONCILIATION_CONFLICT",expected.getCode());}
        assertEquals("refund-pay-1",jdbc.queryForObject("select refund_id from ab_exception_payment where out_trade_no='order-pay-1'",String.class));
        d.setRefundId("refund-pay-1");
        assertEquals("FAILED",teams.complete(reconciliation(d,closed(2))).getTeamStatus());
    }

    @Test public void futureExceptionalPaymentCannotResolveTeam() {
        expired(); trades.close(closeRequest(2));
        String future=V2Format.utc(jdbc.queryForObject("select now(3)+interval 1 day",Timestamp.class));
        try {teams.complete(reconciliation(outside(1,future),closed(2))); org.junit.Assert.fail("future payment accepted");}
        catch (V2Failure expected) {assertEquals("RECONCILIATION_CONFLICT",expected.getCode());}
        assertEquals("RECONCILING",jdbc.queryForObject("select team_status from ab_team where team_id='team-pay'",String.class));
    }

    @Test public void completedTeamRemembersFirstCompletionFactsWithoutReversingResult() {
        expired(); String paidAt=V2Format.utc(jdbc.queryForObject("select now(3)-interval 30 second",Timestamp.class));
        events.process(payment("reconcile-paid1",1,paidAt),"zj-market");
        events.process(payment("reconcile-paid2",2,paidAt),"zj-market");
        ReconciliationDecision one=closed(1),two=closed(2);
        one.setDisposition("PAID_ACCEPTED"); one.setPaymentId("payment-pay-1");
        two.setDisposition("PAID_ACCEPTED"); two.setPaymentId("payment-pay-2");
        ReconciliationCompleteRequest r=reconciliation(one,two);
        assertEquals("COMPLETED",teams.complete(r).getTeamStatus());
        r.setCheckedAt(V2Format.utc(new Timestamp(System.currentTimeMillis()+1000)));
        try {teams.complete(r); org.junit.Assert.fail("same completion id changed facts");}
        catch (V2Failure expected) {assertEquals("RECONCILIATION_CONFLICT",expected.getCode());}
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_outbox where event_type='TEAM_FAILED'",Long.class).longValue());
    }

    private ReserveRequest newOrder(String number,String user,String goods) {
        ReserveRequest r=new ReserveRequest(); r.setSource("s01"); r.setChannel("c01"); r.setUserId(user);
        r.setOutTradeNo(number); r.setGoodsId(goods); r.setActivityId("1"); r.setProductVersion("1");
        r.setQuantity(1); r.setExpectedPayPrice("80.00"); r.setCurrency("CNY"); return r;
    }

    private void quoteWithLimit(int limit) {
        Offer offer=new Offer(); offer.setOriginalPrice("100.00"); offer.setDeductionPrice("20.00"); offer.setPayPrice("80.00");
        V2MarketingEngine.Quote quote=new V2MarketingEngine.Quote(); quote.eligible=true; quote.offer=offer;
        quote.activity=GroupBuyActivityDiscountVO.builder().activityId(1L).target(3).validTime(10).takeLimitCount(limit)
                .startTime(new Date(System.currentTimeMillis()-60000)).endTime(new Date(System.currentTimeMillis()+60000)).build();
        when(marketing.quote(any(TrialRequest.class),eq(true))).thenReturn(quote);
    }

    @Test public void concurrentDifferentSkuReservationsCannotBypassUserQuota() throws Exception {
        quoteWithLimit(1); CountDownLatch start=new CountDownLatch(1); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<String> a=pool.submit(()->{start.await(); try {trades.reserve(newOrder("quota-one","quota-user","sku-one")); return "SUCCESS";}
                catch (V2Failure e) {return e.getCode();}});
            Future<String> b=pool.submit(()->{start.await(); try {trades.reserve(newOrder("quota-two","quota-user","sku-two")); return "SUCCESS";}
                catch (V2Failure e) {return e.getCode();}});
            start.countDown(); java.util.List<String> outcomes=Arrays.asList(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            org.junit.Assert.assertTrue(outcomes.contains("SUCCESS")); org.junit.Assert.assertTrue(outcomes.contains("E0103"));
            assertEquals(1L,jdbc.queryForObject("select count(*) from ab_trade_order where user_id='quota-user'",Long.class).longValue());
        } finally {pool.shutdownNow();}
    }

    @Test public void reservationRetryPreservesSnapshotAfterAdmissionAndProductChange() {
        quoteWithLimit(10); ReserveRequest r=newOrder("retry-snapshot","retry-user","sku-one");
        Reservation original=trades.reserve(r);
        when(marketing.quote(any(TrialRequest.class),eq(true))).thenThrow(new V2Failure("E0105"));
        Reservation retry=trades.reserve(r); assertEquals(original.getOrderId(),retry.getOrderId());
        assertEquals("80.00",retry.getPayPrice());
        r.setExpectedPayPrice("81.00");
        try {trades.reserve(r); org.junit.Assert.fail("changed retry accepted");}
        catch (V2Failure expected) {assertEquals("E0107",expected.getCode());}
    }

    @Test public void ordinaryCompletedTeamRefundIsRejectedInsteadOfWaitingForever() {
        String paidAt=V2Format.utc(jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class));
        events.process(payment("complete-refund-one",1,paidAt),"zj-market");
        events.process(payment("complete-refund-two",2,paidAt),"zj-market");
        assertEquals("REFUND_CONFLICT",events.process(refund("completed-refund",1,"1","PENDING",null),"zj-market").getCode());
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_waiting_dependency",Long.class).longValue());
    }

    @Test public void cancelledOrderRefundCanRecordPaymentInsideOriginalWindowWithoutRejoining() {
        String paidAt=V2Format.utc(jdbc.queryForObject("select now(3)-interval 10 second",Timestamp.class));
        trades.close(closeRequest(1));
        assertEquals("APPLIED",events.process(refund("cancelled-payment-refund",1,"1","PENDING",paidAt),"zj-market").getOutcome());
        assertEquals("CANCELLED",jdbc.queryForObject("select order_status from ab_trade_order where out_trade_no='order-pay-1'",String.class));
        assertEquals(0,jdbc.queryForObject("select paid_count from ab_team where team_id='team-pay'",Integer.class).intValue());
    }
}
