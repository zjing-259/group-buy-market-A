package cn.bugstack.test.phase1;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.junit.Assert.*;

/** Lease validity must be checked after a result writer finishes waiting for the database lock. */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PhaseOneTradeTest.Config.class)
public class PhaseThreeReauditTest {
    @Resource private JdbcTemplate jdbc;
    @Resource private ITradeRepository repository;
    @Resource private PlatformTransactionManager transactionManager;

    @Before public void seed() {
        PhaseOneTradeTest fixture = new PhaseOneTradeTest();
        ReflectionTestUtils.setField(fixture, "jdbc", jdbc);
        fixture.seed();
    }

    @Test public void resultWaitingPastLeaseMustNotBeAccepted() throws Exception {
        jdbc.update("insert into notify_task(activity_id,team_id,notify_url,notify_count,notify_status,parameter_json) " +
                "values(900001,'leasewt','http://127.0.0.1/callback',0,0,'{}')");
        NotifyTaskEntity owner = repository.claimNotifyTask("leasewt");
        assertNotNull(owner);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        final Future<Integer>[] result = new Future[1];
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select team_id from notify_task where team_id='leasewt' for update", String.class);
                jdbc.update("update notify_task set lease_until=now(3)+interval 3 second where team_id='leasewt'");
                result[0] = pool.submit(() -> repository.updateNotifyTaskStatusSuccess(owner));
                try {
                    long barrier = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    boolean waiting = false;
                    while (System.nanoTime() < barrier) {
                        int n = jdbc.queryForObject("select count(*) from performance_schema.data_lock_waits w " +
                                "join performance_schema.data_locks l on w.requesting_engine_lock_id=l.engine_lock_id " +
                                "where l.object_schema='group_buy_market_phase1_test' and l.object_name='notify_task'", Integer.class);
                        if (n > 0) { waiting = true; break; }
                        Thread.sleep(20);
                    }
                    assertTrue("Result writer must reach its row-lock wait before lease expiry", waiting);
                    assertTrue(jdbc.queryForObject("select lease_until>now(3) from notify_task where team_id='leasewt'", Boolean.class));
                    Thread.sleep(3500);
                    assertTrue(jdbc.queryForObject("select lease_until<=now(3) from notify_task where team_id='leasewt'", Boolean.class));
                    assertFalse(result[0].isDone());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return null;
            });
            assertEquals("Expired lease accepted a result after waiting for the row lock", Integer.valueOf(0), result[0].get(10, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(4), jdbc.queryForObject("select notify_status from notify_task where team_id='leasewt'", Integer.class));
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }

    @Test public void failureWaitingPastLeaseMustNotBeAccepted() throws Exception {
        expiredResultCannotWrite("leaserr", repository::updateNotifyTaskStatusError);
    }

    @Test public void retryWaitingPastLeaseMustNotBeAccepted() throws Exception {
        expiredResultCannotWrite("leasert", repository::updateNotifyTaskStatusRetry);
    }

    @Test public void claimWaitingForRowLockStartsFreshLeaseAfterWait() throws Exception {
        String team = "claimwt";
        jdbc.update("insert into notify_task(activity_id,team_id,notify_url,notify_count,notify_status,parameter_json) " +
                "values(900001,?,'http://127.0.0.1/callback',1,2,'{}')", team);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        final Future<NotifyTaskEntity>[] result = new Future[1];
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select team_id from notify_task where team_id=? for update", String.class, team);
                jdbc.update("update notify_task set next_attempt_time=now(3)+interval 3 second where team_id=?", team);
                assertTrue(jdbc.queryForObject("select next_attempt_time>now(3) from notify_task where team_id=?", Boolean.class, team));
                result[0] = pool.submit(() -> repository.claimNotifyTask(team));
                try {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    boolean waiting = false;
                    while (System.nanoTime() < deadline) {
                        int n = jdbc.queryForObject("select count(*) from performance_schema.data_lock_waits w " +
                                "join performance_schema.data_locks l on w.requesting_engine_lock_id=l.engine_lock_id " +
                                "where l.object_schema='group_buy_market_phase1_test' and l.object_name='notify_task'", Integer.class);
                        if (n > 0) { waiting = true; break; }
                        Thread.sleep(20);
                    }
                    assertTrue("Claim did not reach the row-lock wait", waiting);
                    Thread.sleep(3500);
                    assertTrue(jdbc.queryForObject("select next_attempt_time<=now(3) from notify_task where team_id=?", Boolean.class, team));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return null;
            });
            NotifyTaskEntity claimed = result[0].get(10,TimeUnit.SECONDS);
            assertNotNull("Due task was skipped using time from before its lock wait", claimed);
            assertEquals(Integer.valueOf(2), claimed.getNotifyCount());
            assertTrue(jdbc.queryForObject("select timestampdiff(second,now(3),lease_until)>=55 from notify_task where team_id=?", Boolean.class, team));
        } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS); }
    }

    private void expiredResultCannotWrite(String team, Function<NotifyTaskEntity,Integer> update) throws Exception {
        jdbc.update("insert into notify_task(activity_id,team_id,notify_url,notify_count,notify_status,parameter_json) " +
                "values(900001,?,'http://127.0.0.1/callback',0,0,'{}')", team);
        NotifyTaskEntity owner = repository.claimNotifyTask(team);
        owner.setLastError("injected failure");
        owner.setRetryDelaySeconds(60);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        final Future<Integer>[] result = new Future[1];
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select team_id from notify_task where team_id=? for update", String.class, team);
                jdbc.update("update notify_task set lease_until=now(3)+interval 3 second where team_id=?", team);
                result[0] = pool.submit(() -> update.apply(owner));
                try {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    boolean waiting = false;
                    while (System.nanoTime() < deadline) {
                        int n = jdbc.queryForObject("select count(*) from performance_schema.data_lock_waits w " +
                                "join performance_schema.data_locks l on w.requesting_engine_lock_id=l.engine_lock_id " +
                                "where l.object_schema='group_buy_market_phase1_test' and l.object_name='notify_task'", Integer.class);
                        if (n > 0) { waiting = true; break; }
                        Thread.sleep(20);
                    }
                    assertTrue("Result writer did not reach its row-lock wait", waiting);
                    Thread.sleep(3500);
                    assertTrue(jdbc.queryForObject("select lease_until<=now(3) from notify_task where team_id=?", Boolean.class, team));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return null;
            });
            assertEquals(Integer.valueOf(0), result[0].get(10,TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(4), jdbc.queryForObject("select notify_status from notify_task where team_id=?", Integer.class, team));
        } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS); }
    }
}
