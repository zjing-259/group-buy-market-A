package cn.bugstack.test.phase1;

import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.domain.trade.service.TradeRequestValidation;
import cn.bugstack.infrastructure.adapter.port.TradePort;
import cn.bugstack.infrastructure.gateway.GroupBuyNotifyService;
import cn.bugstack.types.exception.AppException;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import javax.annotation.Resource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PhaseOneTradeTest.Config.class)
public class PhaseThreeNotifyTest {
    @Resource private JdbcTemplate jdbc;
    @Resource private ITradeRepository repository;
    @Resource private ITradeSettlementOrderService settlement;
    @Resource private ITradePort port;

    @Before public void seed() {
        PhaseOneTradeTest fixture = new PhaseOneTradeTest();
        ReflectionTestUtils.setField(fixture, "jdbc", jdbc);
        fixture.seed();
        reset(port);
    }

    private void task(String team) {
        jdbc.update("insert into notify_task(activity_id,team_id,notify_url,notify_count,notify_status,parameter_json) values(900001,?,'http://127.0.0.1/callback',0,0,'{}')", team);
    }
    private int value(String team, String column) {
        return jdbc.queryForObject("select " + column + " from notify_task where team_id=?", Integer.class, team);
    }
    private void due(String team) {
        jdbc.update("update notify_task set next_attempt_time=now(3)-interval 1 second where team_id=?", team);
    }
    private void expire(String team) {
        jdbc.update("update notify_task set lease_until=now(3)-interval 1 second,next_attempt_time=now(3)-interval 1 second where team_id=?", team);
    }

    @Test public void twoFailuresThenSuccessRespectBackoffAndStop() throws Exception {
        task("recover");
        when(port.groupBuyNotify(any())).thenReturn("error", "unexpected", "success");
        for (int attempt=1; attempt<=3; attempt++) {
            settlement.execSettlementNotifyJob("recover");
            assertEquals(attempt, value("recover", "notify_count"));
            assertEquals(attempt==3 ? 1 : 2, value("recover", "notify_status"));
            if (attempt<3) {
                long delay=jdbc.queryForObject("select timestampdiff(second,now(3),next_attempt_time) from notify_task where team_id='recover'", Long.class);
                assertTrue(delay >= (60L << (attempt-1))-3);
                settlement.execSettlementNotifyJob("recover");
                assertEquals(attempt, value("recover", "notify_count"));
                due("recover");
            }
        }
        settlement.execSettlementNotifyJob("recover");
        verify(port,times(3)).groupBuyNotify(any());
    }

    @Test public void exceptionsAndNullReachExactlyFiveAttempts() throws Exception {
        task("limit");
        when(port.groupBuyNotify(any())).thenThrow(new IOException("network")).thenReturn(null);
        for (int i=1;i<=5;i++) {
            settlement.execSettlementNotifyJob("limit");
            assertFalse(Thread.currentThread().isInterrupted());
            assertEquals(i,value("limit","notify_count"));
            assertEquals(i==5 ? 3 : 2,value("limit","notify_status"));
            assertNotNull(jdbc.queryForObject("select last_error from notify_task where team_id='limit'",String.class));
            due("limit");
        }
        settlement.execSettlementNotifyJob();
        verify(port,times(5)).groupBuyNotify(any());
    }

    @Test public void simultaneousWorkersOnlySendOnce() throws Exception {
        task("parallel");
        CountDownLatch sending=new CountDownLatch(1), release=new CountDownLatch(1);
        when(port.groupBuyNotify(any())).thenAnswer(invocation -> {
            sending.countDown();
            if (!release.await(10,TimeUnit.SECONDS)) throw new IOException("test barrier timeout");
            return "success";
        });
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> first=pool.submit(() -> settlement.execSettlementNotifyJob("parallel"));
            assertTrue(sending.await(10,TimeUnit.SECONDS));
            assertNull(repository.claimNotifyTask("parallel"));
            settlement.execSettlementNotifyJob("parallel");
            assertEquals(1,value("parallel","notify_count"));
            release.countDown(); first.get(10,TimeUnit.SECONDS);
            assertEquals(1,value("parallel","notify_status"));
            verify(port,times(1)).groupBuyNotify(any());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test public void expiredOwnerCannotOverwriteNewSuccess() {
        task("stale");
        NotifyTaskEntity old=repository.claimNotifyTask("stale");
        expire("stale");
        assertEquals(0,repository.updateNotifyTaskStatusSuccess(old));
        NotifyTaskEntity current=repository.claimNotifyTask("stale");
        assertNotEquals(old.getClaimToken(),current.getClaimToken());
        assertEquals(1,repository.updateNotifyTaskStatusSuccess(current));
        old.setLastError("STALE_FAILURE"); old.setRetryDelaySeconds(60);
        assertEquals(0,repository.updateNotifyTaskStatusRetry(old));
        assertEquals(0,repository.updateNotifyTaskStatusError(old));
        assertEquals(1,value("stale","notify_status"));
        assertEquals(2,value("stale","notify_count"));
        assertNull(repository.claimNotifyTask("stale"));
    }

    @Test public void abandonedClaimsRecoverAndExhaustionIsVisible() throws Exception {
        task("crash");
        for(int i=1;i<=5;i++) {
            assertNotNull(repository.claimNotifyTask("crash"));
            assertEquals(i,value("crash","notify_count"));
            expire("crash");
        }
        settlement.execSettlementNotifyJob();
        assertEquals(3,value("crash","notify_status"));
        assertNull(repository.claimNotifyTask("crash"));
        verifyNoInteractions(port);
    }

    @Test public void legacyOverLimitPendingTaskBecomesVisibleTerminal() throws Exception {
        task("legacy");
        jdbc.update("update notify_task set notify_count=5,notify_status=2 where team_id='legacy'");
        settlement.execSettlementNotifyJob();
        assertEquals(3,value("legacy","notify_status"));
        verifyNoInteractions(port);
    }

    @Test public void schedulerResumesAbandonedClaim() throws Exception {
        task("resume");
        assertNotNull(repository.claimNotifyTask("resume"));
        when(port.groupBuyNotify(any())).thenReturn("success");
        settlement.execSettlementNotifyJob();
        verifyNoInteractions(port);
        expire("resume");
        settlement.execSettlementNotifyJob();
        assertEquals(1,value("resume","notify_status"));
        assertEquals(2,value("resume","notify_count"));
        verify(port,times(1)).groupBuyNotify(any());
    }

    @Test public void badFirstBatchDoesNotStarveLaterHealthyTasks() throws Exception {
        for(int i=0;i<55;i++) task("n"+i);
        when(port.groupBuyNotify(any())).thenAnswer(invocation -> {
            NotifyTaskEntity t=invocation.getArgument(0);
            return Integer.parseInt(t.getTeamId().substring(1)) < 50 ? "error" : "success";
        });
        settlement.execSettlementNotifyJob();
        settlement.execSettlementNotifyJob();
        assertEquals(Integer.valueOf(5),jdbc.queryForObject("select count(*) from notify_task where notify_status=1",Integer.class));
        assertEquals(Integer.valueOf(50),jdbc.queryForObject("select count(*) from notify_task where notify_status=2",Integer.class));
        verify(port,times(55)).groupBuyNotify(any());
    }

    @Test public void invalidAddressesNeverPretendDelivery() throws Exception {
        TradePort realPort=new TradePort();
        GroupBuyNotifyService gateway=mock(GroupBuyNotifyService.class);
        ReflectionTestUtils.setField(realPort,"groupBuyNotifyService",gateway);
        for(String url:new String[]{null,"","暂无","ftp://example.com","http://","http://user:pass@example.com","http://example.com/#fragment","http://example.com:99999"}) {
            try { realPort.groupBuyNotify(NotifyTaskEntity.builder().notifyUrl(url).build()); fail("accepted invalid URL"); }
            catch(AppException expected) { }
        }
        verifyNoInteractions(gateway);
        TradeRequestValidation.callbackUrl("http://127.0.0.1:8080/callback");
    }

    @Test public void httpStatusAndExactBodyBothRequiredAndIoDoesNotInterrupt() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        for(String path:new String[]{"/ok","/error","/badstatus","/other"}) {
            server.createContext(path, exchange -> {
                byte[] body=(path.equals("/ok") || path.equals("/badstatus") ? "success" : path.equals("/error") ? "error" : "SUCCESS").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(path.equals("/badstatus") ? 500 : 200,body.length);
                try(java.io.OutputStream out=exchange.getResponseBody()) { out.write(body); }
                exchange.close();
            });
        }
        server.start();
        GroupBuyNotifyService gateway=new GroupBuyNotifyService();
        OkHttpClient client=new OkHttpClient();
        ReflectionTestUtils.setField(gateway,"okHttpClient",client);
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        try {
            assertEquals("success",gateway.groupBuyNotify(base+"/ok","{}"));
            assertEquals("HTTP_STATUS_500",gateway.groupBuyNotify(base+"/badstatus","{}"));
            for(String path:new String[]{"/error","/other"}) assertEquals("UNEXPECTED_RESPONSE_BODY",gateway.groupBuyNotify(base+path,"{}"));
            server.stop(0);
            try { gateway.groupBuyNotify(base+"/ok","{}"); fail(); } catch(IOException expected) { }
            assertFalse(Thread.currentThread().isInterrupted());
        } finally { server.stop(0); client.connectionPool().evictAll(); client.dispatcher().executorService().shutdownNow(); }
    }

    @Test public void realInterruptionPreservesFlagAndPersistsRetry() throws Exception {
        task("intrpt");
        when(port.groupBuyNotify(any())).thenThrow(new InterruptedException("stop"));
        try {
            settlement.execSettlementNotifyJob("intrpt");
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(2,value("intrpt","notify_status"));
    }
}
