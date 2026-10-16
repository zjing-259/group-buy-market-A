package cn.bugstack.test.v2;

import cn.bugstack.v2.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes=V2OutboxRecoveryTest.Config.class)
public class V2OutboxRecoveryTest {
    @Configuration @Import({V2PaymentFlowTest.Config.class,V2OutboxStore.class}) public static class Config { }
    @Resource private JdbcTemplate jdbc;
    @Resource private V2OutboxStore store;
    @Resource private DataSource dataSource;

    @Before public void seed() {
        V2TestDatabase.assertIsolated(jdbc);
        jdbc.update("delete from ab_outbox");
        jdbc.update("insert into ab_outbox(event_id,topic,event_type,source,channel,body,body_hash) " +
                "values('recover-event','A_TEAM_EVENTS-test','TEAM_COMPLETED','s01','c01','{\"fact\":1}',?)",V2Format.sha256("{\"fact\":1}"));
    }

    @Test public void expiredClaimRecoversSameIdentityAndOldTokenCannotPublish() {
        V2OutboxStore.Claim old=store.claim("recover-event"); assertNotNull(old); assertNull(store.claim(old.eventId));
        jdbc.update("update ab_outbox set lease_until=now(3)-interval 1 second where event_id=?",old.eventId);
        V2OutboxStore.Claim current=store.claim(old.eventId); assertNotNull(current);
        assertEquals(old.body,current.body); assertEquals(old.eventId,current.eventId); assertEquals(2,current.attempts);
        assertFalse(store.published(old)); assertFalse(store.failed(old,"stale"));
        assertTrue(store.published(current)); assertNull(store.claim(current.eventId));
    }

    @Test public void publishingWaitsForRowLockAndCannotUseStatementStartTimeAsLeaseTime() throws Exception {
        V2OutboxStore.Claim c=store.claim("recover-event");
        jdbc.update("update ab_outbox set lease_until=now(3)+interval 2 second where event_id=?",c.eventId);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try (Connection lock=dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (Statement statement=lock.createStatement()) {statement.executeQuery("select event_id from ab_outbox where event_id='recover-event' for update").close();}
            Future<Boolean> publishing=pool.submit(()->store.published(c));
            try {Thread.sleep(2300);} finally {lock.commit();}
            assertFalse(publishing.get(5,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertEquals("CLAIMED",jdbc.queryForObject("select status from ab_outbox where event_id=?",String.class,c.eventId));
    }

    @Test public void failureBackoffContinuesBeyondFiveAttemptsWithoutDiscardingFact() {
        for (int attempt=1;attempt<=6;attempt++) {
            V2OutboxStore.Claim c=store.claim("recover-event"); assertEquals(attempt,c.attempts);
            assertTrue(store.failed(c,"broker unavailable"));
            long delay=jdbc.queryForObject("select timestampdiff(second,now(3),next_attempt_at) from ab_outbox where event_id=?",Long.class,c.eventId);
            int wanted=attempt==1?60:attempt==2?120:attempt==3?240:480;
            assertTrue("wrong backoff",delay>=wanted-2 && delay<=wanted);
            assertEquals("PENDING",jdbc.queryForObject("select status from ab_outbox where event_id=?",String.class,c.eventId));
            jdbc.update("update ab_outbox set next_attempt_at=now(3)-interval 1 second where event_id=?",c.eventId);
        }
        assertEquals("{\"fact\":1}",jdbc.queryForObject("select body from ab_outbox where event_id='recover-event'",String.class));
    }
}
