package cn.bugstack.test.phase1;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.valobj.SCSkuActivityVO;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.domain.activity.service.trial.node.MarketNode;
import cn.bugstack.config.ThreadPoolConfig;
import cn.bugstack.config.ThreadPoolConfigProperties;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import javax.annotation.Resource;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PhaseOneTradeTest.Config.class)
public class PhaseFourTimeoutTest {
    @Resource private MarketNode marketNode;
    @Resource private ThreadPoolExecutor threadPoolExecutor;
    @Resource private IActivityRepository repository;

    private MarketProductEntity request() {
        return MarketProductEntity.builder().userId("timeout").source("s01").channel("c01").goodsId("testsku").build();
    }

    @Test public void twoBlockingLookupsShareOneFiveSecondBudgetAndAreCancelled() throws Exception {
        IActivityRepository delayed=mock(IActivityRepository.class);
        CountDownLatch started=new CountDownLatch(2), interrupted=new CountDownLatch(2);
        when(delayed.querySCSkuActivityBySCGoodsId(anyString(),anyString(),anyString())).thenAnswer(invocation -> {
            started.countDown();
            try { Thread.sleep(30000); } catch(InterruptedException e) { interrupted.countDown(); throw e; }
            return SCSkuActivityVO.builder().activityId(900001L).build();
        });
        when(delayed.querySkuByGoodsId(anyString())).thenAnswer(invocation -> {
            started.countDown();
            try { Thread.sleep(30000); } catch(InterruptedException e) { interrupted.countDown(); throw e; }
            return null;
        });
        ReflectionTestUtils.setField(marketNode,"repository",delayed);
        long start=System.nanoTime();
        try {
            try { marketNode.apply(request(),new DefaultActivityStrategyFactory.DynamicContext()); fail(); }
            catch(TimeoutException expected) { }
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            assertTrue("Trial exceeded its overall deadline: "+elapsed,elapsed<6500);
            assertEquals(0,started.getCount());
            assertTrue("Both blocked lookups should be interrupted",interrupted.await(2,TimeUnit.SECONDS));
        } finally { ReflectionTestUtils.setField(marketNode,"repository",repository); }
    }

    @Test public void failedLookupCancelsSiblingAndPoolCanRunAgain() throws Exception {
        IActivityRepository failing=mock(IActivityRepository.class);
        CountDownLatch interrupted=new CountDownLatch(1);
        when(failing.querySCSkuActivityBySCGoodsId(anyString(),anyString(),anyString())).thenAnswer(invocation -> {
            Thread.sleep(150);
            throw new IllegalStateException("injected lookup failure");
        });
        when(failing.querySkuByGoodsId(anyString())).thenAnswer(invocation -> {
            try { Thread.sleep(30000); } catch(InterruptedException e) { interrupted.countDown(); throw e; }
            return null;
        });
        ReflectionTestUtils.setField(marketNode,"repository",failing);
        try {
            try { marketNode.apply(request(),new DefaultActivityStrategyFactory.DynamicContext()); fail(); }
            catch(ExecutionException expected) { assertTrue(expected.getCause() instanceof IllegalStateException); }
            assertTrue(interrupted.await(2,TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(7),threadPoolExecutor.submit(() -> 7).get(2,TimeUnit.SECONDS));
        } finally { ReflectionTestUtils.setField(marketNode,"repository",repository); }
    }

    @Test public void callerRunsAndDiscardPoliciesAreRejected() throws Exception {
        ThreadPoolConfig config=new ThreadPoolConfig();
        ThreadPoolConfigProperties properties=new ThreadPoolConfigProperties();
        for(String policy:new String[]{"CallerRunsPolicy","DiscardPolicy","DiscardOldestPolicy"}) {
            properties.setPolicy(policy);
            try { config.threadPoolExecutor(properties); fail("Accepted "+policy); }
            catch(IllegalArgumentException expected) { }
        }
    }
}
