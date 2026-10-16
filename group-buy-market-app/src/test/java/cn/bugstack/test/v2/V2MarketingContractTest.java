package cn.bugstack.test.v2;

import cn.bugstack.api.v2.*;
import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.IDiscountCalculateService;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.v2.*;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@RunWith(SpringRunner.class) @ContextConfiguration(classes=V2MarketingContractTest.Config.class)
public class V2MarketingContractTest {
    @Configuration @Import(V2MarketingEngine.class) public static class Config {
        @Bean(destroyMethod="close") public HikariDataSource dataSource() {return new V2PaymentFlowTest.Config().dataSource();}
        @Bean public JdbcTemplate jdbcTemplate(HikariDataSource ds) {return new JdbcTemplate(ds);}
        @Bean public FactoryBean<IActivityRepository> activityRepository() {return mocked(IActivityRepository.class);}
        @Bean public FactoryBean<DCCService> dcc() {return mocked(DCCService.class);}
        @Bean public Map<String,IDiscountCalculateService> discounts() {
            Map<String,IDiscountCalculateService> map=new HashMap<>(); map.put("ZJ",mock(IDiscountCalculateService.class)); return map;
        }
        private static <T> FactoryBean<T> mocked(Class<T> type) {
            return new FactoryBean<T>() {
                private final T value=mock(type);
                public T getObject() {return value;} public Class<?> getObjectType() {return type;}
                public boolean isSingleton() {return true;}
            };
        }
    }
    @Resource private JdbcTemplate jdbc;
    @Resource private IActivityRepository activityRepository;
    @Resource private DCCService dcc;
    @Resource private Map<String,IDiscountCalculateService> discounts;
    @Resource private V2MarketingEngine marketing;
    private TrialRequest request;

    private GroupBuyActivityDiscountVO activity(int status,Date start,Date end,String tag,String scope) {
        return GroupBuyActivityDiscountVO.builder().activityId(991201L).status(status).startTime(start).endTime(end)
                .target(2).validTime(10).tagId(tag).tagScope(scope)
                .groupBuyDiscount(GroupBuyActivityDiscountVO.GroupBuyDiscount.builder().marketPlan("ZJ").build()).build();
    }
    @Before public void seed() {
        V2TestDatabase.assertIsolated(jdbc);
        jdbc.update("delete from sc_sku_activity where activity_id=991201");
        jdbc.update("insert into sc_sku_activity(source,channel,activity_id,goods_id) values('s01','c01',991201,'marketing-test')");
        jdbc.update("delete from ab_product_projection where goods_id='marketing-test'");
        jdbc.update("insert into ab_product_projection values('s01','c01','marketing-test',1,'Product',100.00,'CNY','ON_SALE','test',now(3))");
        reset(activityRepository,dcc,discounts.get("ZJ"));
        when(dcc.iscutRange(anyString())).thenReturn(true);
        when(discounts.get("ZJ").calculate(anyString(),any(BigDecimal.class),any())).thenReturn(new BigDecimal("80.00"));
        when(activityRepository.queryGroupBuyActivityDiscountVO(991201L)).thenReturn(activity(1,
                new Date(System.currentTimeMillis()-60000),new Date(System.currentTimeMillis()+60000),null,null));
        request=new TrialRequest(); request.setSource("s01"); request.setChannel("c01"); request.setUserId("CaseOwner");
        request.setGoodsId("marketing-test"); request.setProductVersion("1"); request.setQuantity(1);
    }
    @After public void clean() {
        jdbc.update("delete from sc_sku_activity where activity_id=991201");
        jdbc.update("delete from ab_product_projection where goods_id='marketing-test'");
    }
    private void fails(String code) {
        try {marketing.trial(request); fail("trial should reject: "+code);}
        catch (V2Failure expected) {assertEquals(code,expected.getCode());}
    }
    @Test public void inactiveAndExpiredActivitiesAreUnavailable() {
        when(activityRepository.queryGroupBuyActivityDiscountVO(991201L)).thenReturn(activity(0,
                new Date(System.currentTimeMillis()-60000),new Date(System.currentTimeMillis()+60000),null,null));
        assertFalse(marketing.trial(request).getAvailable()); request.setActivityId("991201"); fails("E0101");
        when(activityRepository.queryGroupBuyActivityDiscountVO(991201L)).thenReturn(activity(1,
                new Date(System.currentTimeMillis()-60000),new Date(System.currentTimeMillis()-1000),null,null));
        fails("E0102"); request.setActivityId(null); assertFalse(marketing.trial(request).getAvailable());
    }
    @Test public void strictTaggedEligibilityHidesOfferFromNonmembers() {
        when(activityRepository.queryGroupBuyActivityDiscountVO(991201L)).thenReturn(activity(1,
                new Date(System.currentTimeMillis()-60000),new Date(System.currentTimeMillis()+60000),"tag-test","1,2"));
        TrialResponse hidden=marketing.trial(request); assertFalse(hidden.getVisible()); assertFalse(hidden.getEligible()); assertNull(hidden.getOffer());
        when(activityRepository.isTagCrowRange("tag-test","CaseOwner")).thenReturn(true);
        assertTrue(marketing.trial(request).getEligible()); assertNotNull(marketing.trial(request).getOffer());
        request.setUserId("caseowner"); assertFalse(marketing.trial(request).getEligible());
    }
    @Test public void productVersionSaleStatusAndChannelAreChecked() {
        assertEquals("80.00",marketing.trial(request).getOffer().getPayPrice());
        request.setProductVersion("2"); fails("PRODUCT_VERSION_MISMATCH"); request.setProductVersion("1");
        jdbc.update("update ab_product_projection set sale_status='OFF_SALE' where goods_id='marketing-test'"); fails("PRODUCT_NOT_SALEABLE");
        request.setChannel("c02"); fails("PRODUCT_NOT_FOUND");
    }
    @Test public void blacklistAndDependencyFailureAreNotReportedAsNoActivity() {
        when(dcc.isSCBlackIntercept("s01","c01")).thenReturn(true); fails("E0105");
        when(dcc.isSCBlackIntercept("s01","c01")).thenReturn(false);
        when(activityRepository.queryGroupBuyActivityDiscountVO(991201L)).thenThrow(new IllegalStateException("repository unavailable"));
        try {marketing.trial(request); fail("dependency failure hidden");}
        catch (IllegalStateException expected) {assertEquals("repository unavailable",expected.getMessage());}
    }
}
