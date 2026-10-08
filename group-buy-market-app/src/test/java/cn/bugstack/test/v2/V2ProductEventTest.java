package cn.bugstack.test.v2;

import cn.bugstack.api.v2.EventProcessingResult;
import cn.bugstack.v2.V2EventEngine;
import cn.bugstack.v2.V2OutboxService;
import cn.bugstack.v2.V2PaymentEngine;
import cn.bugstack.v2.V2PaymentIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.Resource;
import javax.sql.DataSource;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes=V2ProductEventTest.Config.class)
public class V2ProductEventTest {
    @Configuration
    @EnableTransactionManagement
    @Import({V2EventEngine.class,V2OutboxService.class,V2PaymentIdentity.class})
    public static class Config {
        @Bean(destroyMethod="close") public HikariDataSource dataSource() {
            String password=System.getenv("PHASE1_DB_PASSWORD");
            if (password==null) throw new IllegalStateException("Set PHASE1_DB_PASSWORD");
            HikariDataSource ds=new HikariDataSource();
            ds.setJdbcUrl("jdbc:mysql://127.0.0.1:13306/group_buy_market_phase1_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");
            ds.setUsername("root"); ds.setPassword(password); return ds;
        }
        @Bean public JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean public PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean public FactoryBean<V2PaymentEngine> paymentEngine() {
            return new FactoryBean<V2PaymentEngine>() {
                private final V2PaymentEngine instance=mock(V2PaymentEngine.class);
                @Override public V2PaymentEngine getObject() { return instance; }
                @Override public Class<?> getObjectType() { return V2PaymentEngine.class; }
                @Override public boolean isSingleton() { return true; }
            };
        }
    }

    @Resource private JdbcTemplate jdbc;
    @Resource private V2EventEngine events;

    @Before public void clean() {
        assertEquals("group_buy_market_phase1_test",jdbc.queryForObject("select database()",String.class));
        jdbc.update("delete from ab_outbox");
        jdbc.update("delete from ab_waiting_dependency");
        jdbc.update("delete from ab_inbox");
        jdbc.update("delete from ab_quarantine");
        jdbc.update("delete from ab_exception_payment");
        jdbc.update("delete from ab_payment_guard");
        jdbc.update("delete from ab_trade_guard");
        jdbc.update("delete from ab_trade_order");
        jdbc.update("delete from ab_team");
        jdbc.update("delete from ab_product_projection");
    }

    private String event(String id,String payload) {
        return "{\"eventId\":\""+id+"\",\"eventType\":\"PRODUCT_SNAPSHOT_CHANGED\",\"schemaVersion\":1,"+
                "\"producer\":\"zj-market\",\"occurredAt\":\"2026-10-02T04:00:00.123Z\","+
                "\"source\":\"s01\",\"channel\":\"c01\",\"payload\":"+payload+"}";
    }

    @Test public void equalVersionWithReorderedPropertiesIsIgnoredButChangedPriceIsRejected() {
        String first="{\"goodsId\":\"product1\",\"productVersion\":\"1\",\"goodsName\":\"Product\","+
                "\"originalPrice\":\"100.00\",\"currency\":\"CNY\",\"saleStatus\":\"ON_SALE\"}";
        String reordered="{\"saleStatus\":\"ON_SALE\",\"currency\":\"CNY\",\"originalPrice\":\"100.00\","+
                "\"goodsName\":\"Product\",\"productVersion\":\"1\",\"goodsId\":\"product1\"}";
        String changed=reordered.replace("100.00","99.00");
        EventProcessingResult applied=events.process(event("product-applied",first),"zj-market");
        EventProcessingResult ignored=events.process(event("product-ignored",reordered),"zj-market");
        EventProcessingResult rejected=events.process(event("product-rejected",changed),"zj-market");
        assertEquals("APPLIED",applied.getOutcome());
        assertEquals("IGNORED",ignored.getOutcome());
        assertEquals("REJECTED",rejected.getOutcome());
        assertEquals("100.00",jdbc.queryForObject("select original_price from ab_product_projection where goods_id='product1'",
                java.math.BigDecimal.class).setScale(2).toPlainString());
        assertEquals(3L,jdbc.queryForObject("select count(*) from ab_outbox",Long.class).longValue());
    }

    @Test public void rejectedLateRefundDoesNotLeaveExceptionPaymentEvidence() {
        jdbc.update("insert into ab_team(team_id,source,channel,activity_id,goods_id,target_count,reserved_count,paid_count,"+
                "team_status,team_version,valid_start_time,valid_end_time) values('team1','s01','c01',1,'product1',2,0,0,"+
                "'FORMING',1,now()-interval 2 day,now()-interval 1 day)");
        jdbc.update("insert into ab_trade_order(source,channel,out_trade_no,record_kind,order_id,user_id,team_id,"+
                "order_status,record_version,reserved_at,payment_deadline) values('s01','c01','order1','RESERVATION',"+
                "'a-order1','user1','team1','CANCELLED',2,now()-interval 2 day,now()-interval 1 day)");
        String payload="{\"userId\":\"user1\",\"outTradeNo\":\"order1\",\"paymentId\":\"payment1\","+
                "\"refundId\":\"refund1\",\"refundAmount\":\"99.00\",\"currency\":\"CNY\","+
                "\"refundStatus\":\"PENDING\",\"refundVersion\":\"1\",\"occurredAt\":\"2026-10-02T04:00:00.123Z\","+
                "\"paidAt\":\"2026-10-02T04:00:00.123Z\",\"paidAmount\":\"100.00\"}";
        String raw=event("refund-invalid",payload).replace("PRODUCT_SNAPSHOT_CHANGED","REFUND_STATUS_CHANGED");
        EventProcessingResult result=events.process(raw,"zj-market");
        assertEquals("REJECTED",result.getOutcome());
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_exception_payment",Long.class).longValue());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_outbox",Long.class).longValue());
    }

    @Test public void failedTeamStillRecordsLatePaymentRefundForCancelledOrder() {
        jdbc.update("insert into ab_team(team_id,source,channel,activity_id,goods_id,target_count,reserved_count,paid_count,"+
                "team_status,team_version,valid_start_time,valid_end_time) values('team-failed','s01','c01',1,'product1',"+
                "2,0,0,'FAILED',2,now()-interval 2 day,now()-interval 1 day)");
        jdbc.update("insert into ab_trade_order(source,channel,out_trade_no,record_kind,order_id,user_id,team_id,"+
                "order_status,record_version,reserved_at,payment_deadline) values('s01','c01','late-refund','RESERVATION',"+
                "'a-late-refund','user1','team-failed','CANCELLED',2,now()-interval 2 day,now()-interval 1 day)");
        String payload="{\"userId\":\"user1\",\"outTradeNo\":\"late-refund\",\"paymentId\":\"late-payment\","+
                "\"refundId\":\"refund-late\",\"refundAmount\":\"100.00\",\"currency\":\"CNY\","+
                "\"refundStatus\":\"PENDING\",\"refundVersion\":\"1\",\"occurredAt\":\"2026-10-02T04:00:00.123Z\","+
                "\"paidAt\":\"2026-10-02T04:00:00.123Z\",\"paidAmount\":\"100.00\"}";
        String raw=event("refund-late-event",payload).replace("PRODUCT_SNAPSHOT_CHANGED","REFUND_STATUS_CHANGED");
        assertEquals("APPLIED",events.process(raw,"zj-market").getOutcome());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_exception_payment",Long.class).longValue());
        assertEquals("PENDING",jdbc.queryForObject("select refund_status from ab_trade_order where out_trade_no='late-refund'",String.class));
    }

    @Test public void finishingOneConsumerDoesNotEraseAnotherWaitingEventWithSameId() {
        String id="shared-waiting";
        for (String consumer:new String[]{"A_PRODUCT_SYNC","A_TRADE_FACTS"}) {
            jdbc.update("insert into ab_waiting_dependency(event_id,consumer,producer,body,body_hash,next_check_at) " +
                            "values(?,?,'zj-market','{}','test',now()+interval 1 hour)",id,consumer);
        }
        String payload="{\"goodsId\":\"product1\",\"productVersion\":\"1\",\"goodsName\":\"Product\","+
                "\"originalPrice\":\"100.00\",\"currency\":\"CNY\",\"saleStatus\":\"ON_SALE\"}";
        assertEquals("APPLIED",events.process(event(id,payload),"zj-market").getOutcome());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_waiting_dependency where event_id=?",Long.class,id).longValue());
        assertEquals("A_TRADE_FACTS",jdbc.queryForObject("select consumer from ab_waiting_dependency where event_id=?",String.class,id));
    }

    @Test public void sameEventIdDifferentBodyIsQuarantinedWithoutOverwritingOriginalResult() {
        String payload="{\"goodsId\":\"product1\",\"productVersion\":\"1\",\"goodsName\":\"Product\","+
                "\"originalPrice\":\"100.00\",\"currency\":\"CNY\",\"saleStatus\":\"ON_SALE\"}";
        String raw=event("immutable-event",payload);
        assertEquals("APPLIED",events.process(raw,"zj-market").getOutcome());
        org.junit.Assert.assertNull(events.process(raw.replace("100.00","90.00"),"zj-market"));
        assertEquals("APPLIED",events.process(raw,"zj-market").getOutcome());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_quarantine where reason='EVENT_ID_BODY_MISMATCH'",Long.class).longValue());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_outbox",Long.class).longValue());
    }

    @Test public void duplicateJsonKeysAndUnknownSchemaAreQuarantined() {
        String payload="{\"goodsId\":\"first\",\"goodsId\":\"second\"}";
        org.junit.Assert.assertNull(events.process(event("duplicate-json",payload),"zj-market"));
        org.junit.Assert.assertNull(events.process(event("unknown-schema","{}").replace("\"schemaVersion\":1","\"schemaVersion\":2"),"zj-market"));
        assertEquals(2L,jdbc.queryForObject("select count(*) from ab_quarantine",Long.class).longValue());
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_inbox",Long.class).longValue());
    }

    @Test public void olderProductCannotOverwriteNewVersionOrSaleStatus() {
        String payload="{\"goodsId\":\"product1\",\"productVersion\":\"2\",\"goodsName\":\"Product\","+
                "\"originalPrice\":\"100.00\",\"currency\":\"CNY\",\"saleStatus\":\"OFF_SALE\"}";
        assertEquals("APPLIED",events.process(event("product-new",payload),"zj-market").getOutcome());
        String older=payload.replace("\"productVersion\":\"2\"","\"productVersion\":\"1\"").replace("OFF_SALE","ON_SALE");
        assertEquals("IGNORED",events.process(event("product-old",older),"zj-market").getOutcome());
        assertEquals("OFF_SALE",jdbc.queryForObject("select sale_status from ab_product_projection where goods_id='product1'",String.class));
    }

    @Test public void numericIdentifiersAndStringSchemaVersionsAreNotSilentlyCoerced() {
        String payload="{\"goodsId\":123,\"productVersion\":\"1\",\"goodsName\":\"Product\","+
                "\"originalPrice\":\"100.00\",\"currency\":\"CNY\",\"saleStatus\":\"ON_SALE\"}";
        assertEquals("0002",events.process(event("numeric-id",payload),"zj-market").getCode());
        org.junit.Assert.assertNull(events.process(event("text-schema","{}").replace("\"schemaVersion\":1","\"schemaVersion\":\"1\""),"zj-market"));
        assertEquals(0L,jdbc.queryForObject("select count(*) from ab_product_projection",Long.class).longValue());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ab_quarantine",Long.class).longValue());
    }
}
