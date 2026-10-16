package cn.bugstack.test.v2;

import cn.bugstack.api.v2.*;
import cn.bugstack.v2.*;
import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.IDiscountCalculateService;
import cn.bugstack.infrastructure.dcc.DCCService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real MySQL on a dedicated TD01 schema; mocked marketing repository isolates discovery rules. */
@RunWith(SpringRunner.class) @ContextConfiguration(classes=V2TeamDiscoveryTest.Config.class)
public class V2TeamDiscoveryTest {
    @Configuration @EnableTransactionManagement @Import({V2MarketingEngine.class,V2TeamDiscoveryEngine.class})
    public static class Config {
        @Bean(destroyMethod="close") public HikariDataSource dataSource() {
            String url=System.getenv("TD01_JDBC_URL");
            if (url==null || !url.matches(".*[/:]group_buy_market_td01_[0-9_]+[?].*")) throw new IllegalStateException("Dedicated TD01 schema required");
            HikariDataSource ds=new HikariDataSource(); ds.setJdbcUrl(url);
            ds.setUsername(System.getenv("TD01_DB_USER")); ds.setPassword(System.getenv("TD01_DB_PASSWORD"));
            ds.setMaximumPoolSize(4); return ds;
        }
        @Bean public JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean public PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean public FactoryBean<IActivityRepository> activityRepository() { return mocked(IActivityRepository.class); }
        @Bean public FactoryBean<DCCService> dcc() { return mocked(DCCService.class); }
        @Bean public Map<String,IDiscountCalculateService> discounts() { Map<String,IDiscountCalculateService> map=new HashMap<>();map.put("ZJ",mock(IDiscountCalculateService.class));return map; }
        private static <T> FactoryBean<T> mocked(Class<T> type) {
            return new FactoryBean<T>() {
                final T value=mock(type); public T getObject(){return value;} public Class<?> getObjectType(){return type;} public boolean isSingleton(){return true;}
            };
        }
    }
    @Resource private JdbcTemplate jdbc;
    @Resource private V2TeamDiscoveryEngine discovery;
    @Resource private IActivityRepository repository;
    @Resource private DCCService dcc;
    @Resource private Map<String,IDiscountCalculateService> discounts;
    private TeamDiscoveryRequest request;
    private java.sql.Timestamp now;
    @Before public void seed() {
        assertTrue(jdbc.queryForObject("select database()",String.class).startsWith("group_buy_market_td01_"));
        jdbc.update("delete from ab_trade_order where out_trade_no like 'td01-unit-%'");
        jdbc.update("delete from ab_team where team_id like 'td01-unit-%'");
        jdbc.update("delete from ab_product_projection where goods_id='td01-unit-goods'");
        jdbc.update("delete from sc_sku_activity where goods_id='td01-unit-goods'");
        jdbc.update("insert into ab_product_projection values('td01','web','td01-unit-goods',1,'Unit product',100.00,'CNY','ON_SALE','unit',now(3))");
        jdbc.update("insert into sc_sku_activity(source,channel,activity_id,goods_id) values('td01','web',714101,'td01-unit-goods')");
        now=jdbc.queryForObject("select now(3)",java.sql.Timestamp.class);
        reset(repository,dcc,discounts.get("ZJ"));when(dcc.iscutRange(anyString())).thenReturn(true);
        when(discounts.get("ZJ").calculate(anyString(),any(BigDecimal.class),any())).thenReturn(new BigDecimal("90.00"));
        when(repository.queryGroupBuyActivityDiscountVO(714101L)).thenReturn(activity(null,null));
        request=new TeamDiscoveryRequest();request.setSource("td01");request.setChannel("web");request.setUserId("CaseOwner");
        request.setGoodsId("td01-unit-goods");request.setActivityId("714101");request.setProductVersion("1");request.setLimit(3);
    }
    GroupBuyActivityDiscountVO activity(String tag,String scope) {
        return GroupBuyActivityDiscountVO.builder().activityId(714101L).status(1).startTime(new Date(now.getTime()-60000))
                .endTime(new Date(now.getTime()+600000)).target(3).validTime(120).tagId(tag).tagScope(scope)
                .groupBuyDiscount(GroupBuyActivityDiscountVO.GroupBuyDiscount.builder().marketPlan("ZJ").build()).build();
    }
    void team(String id,int reserved,int paid,String state,long end) {
        jdbc.update("insert into ab_team(team_id,source,channel,activity_id,goods_id,target_count,reserved_count,paid_count,team_status,team_version,valid_start_time,valid_end_time) values(?,'td01','web',714101,'td01-unit-goods',3,?,?,?,1,?,?)",id,reserved,paid,state,new java.sql.Timestamp(now.getTime()-60000),new java.sql.Timestamp(end));
    }
    void fails(String code) {try{discovery.list(request);fail("Expected "+code);}catch(V2Failure e){assertEquals(code,e.getCode());}}
    @Test public void acceptedPaymentAndVacancyRequiredEvenWhenSchedulerHasNotRun() {
        team("td01-unit-visible",2,1,"FORMING",now.getTime()+600000);
        team("td01-unit-unpaid",1,0,"FORMING",now.getTime()+600000);
        team("td01-unit-full",3,1,"FORMING",now.getTime()+600000);
        team("td01-unit-expired",2,1,"FORMING",now.getTime()-1);
        team("td01-unit-reconciling",2,1,"RECONCILING",now.getTime()+600000);
        team("td01-unit-completed",3,3,"COMPLETED",now.getTime()+600000);
        team("td01-unit-failed",2,1,"FAILED",now.getTime()+600000);
        team("td01-unit-invalid-count",1,2,"FORMING",now.getTime()+600000);
        assertEquals(Collections.singletonList("td01-unit-visible"),ids(discovery.list(request)));
        assertEquals("FORMING",jdbc.queryForObject("select team_status from ab_team where team_id='td01-unit-expired'",String.class));
    }
    @Test public void scopeActivityGoodsTargetAndFutureStartAreAllFiltered() {
        for(String suffix:Arrays.asList("channel","source","activity","goods","target","future"))team("td01-unit-"+suffix,2,1,"FORMING",now.getTime()+600000);
        jdbc.update("update ab_team set channel='other' where team_id='td01-unit-channel'");
        jdbc.update("update ab_team set source='other' where team_id='td01-unit-source'");
        jdbc.update("update ab_team set activity_id=714102 where team_id='td01-unit-activity'");
        jdbc.update("update ab_team set goods_id='other' where team_id='td01-unit-goods'");
        jdbc.update("update ab_team set target_count=4 where team_id='td01-unit-target'");
        jdbc.update("update ab_team set valid_start_time=now()+interval 1 day where team_id='td01-unit-future'");
        assertTrue(discovery.list(request).getItems().isEmpty());
    }
    @Test public void exactOwnerExcludedButCaseVariantAndCancelledHistoryRemainDiscoverable() {
        team("td01-unit-own",2,1,"FORMING",now.getTime()+600000);
        jdbc.update("insert into ab_trade_order(source,channel,out_trade_no,record_kind,user_id,team_id,order_status,record_version) values('td01','web','td01-unit-order','RESERVATION','CaseOwner','td01-unit-own','RESERVED',1)");
        assertTrue(discovery.list(request).getItems().isEmpty());request.setUserId("caseowner");assertEquals(1,discovery.list(request).getItems().size());
        request.setUserId("CaseOwner");jdbc.update("update ab_trade_order set order_status='CANCELLED' where out_trade_no='td01-unit-order'");
        assertEquals(1,discovery.list(request).getItems().size());
    }
    @Test public void keysetCursorUsesLastReturnedRowForTiedTimesAndEndsWithNull() {
        for(int i=0;i<5;i++)team("td01-unit-page-"+i,2,1,"FORMING",now.getTime()+600000);
        request.setLimit(2);TeamDiscoveryPage first=discovery.list(request);assertEquals(Arrays.asList("td01-unit-page-0","td01-unit-page-1"),ids(first));assertTrue(first.getHasMore());
        request.setCursor(first.getNextCursor());TeamDiscoveryPage second=discovery.list(request);assertEquals(Arrays.asList("td01-unit-page-2","td01-unit-page-3"),ids(second));
        request.setCursor(second.getNextCursor());TeamDiscoveryPage last=discovery.list(request);assertEquals(Collections.singletonList("td01-unit-page-4"),ids(last));assertFalse(last.getHasMore());assertNull(last.getNextCursor());
    }
    @Test public void everyPageRechecksProductAndStrictQualification() {
        team("td01-unit-eligible",2,1,"FORMING",now.getTime()+600000);
        when(repository.queryGroupBuyActivityDiscountVO(714101L)).thenReturn(activity("members","1,2"));fails("E0110");
        when(repository.isTagCrowRange("members","CaseOwner")).thenReturn(true);assertEquals(1,discovery.list(request).getItems().size());
        request.setProductVersion("2");fails("PRODUCT_VERSION_MISMATCH");request.setProductVersion("1");
        jdbc.update("update ab_product_projection set sale_status='OFF_SALE' where goods_id='td01-unit-goods'");fails("PRODUCT_NOT_SALEABLE");
    }
    @Test public void cursorScopeAndContextAreCheckedBeforeDatabaseLookup() {
        request.setCursor(V2TeamDiscoveryCursor.encode(request,V2Format.utc(now),"last"));request.setUserId("other");fails("0002");
        request.setUserId("CaseOwner");request.setProductVersion("2");fails("TEAM_DISCOVERY_CONTEXT_CHANGED");
        request.setProductVersion("1");request.setCursor("");fails("0002");request.setCursor(null);request.setLimit(11);fails("0002");
    }
    @Test public void returnedDatabaseClockAndBoundariesAreConsistent() {
        team("td01-unit-time",2,1,"FORMING",now.getTime()+600000);
        TeamDiscoveryPage p=discovery.list(request);assertEquals(1,p.getItems().size());
        assertTrue(V2Format.instant(p.getServerTime()).isBefore(V2Format.instant(p.getItems().get(0).getValidEndTime())));
        assertFalse(V2Format.instant(p.getServerTime()).isBefore(V2Format.instant(p.getItems().get(0).getValidStartTime())));
    }
    @Test public void dependencyFailuresAndBlacklistNeverBecomeEmptySuccess() {
        when(dcc.isSCBlackIntercept("td01","web")).thenReturn(true);fails("E0105");
        when(dcc.isSCBlackIntercept("td01","web")).thenReturn(false);
        when(repository.queryGroupBuyActivityDiscountVO(714101L)).thenThrow(new IllegalStateException("repository unavailable"));
        try{discovery.list(request);fail();}catch(IllegalStateException expected){assertEquals("repository unavailable",expected.getMessage());}
    }
    @Test public void candidateStatementCancelsActualSlowMysqlWithinBudget() {
        team("td01-unit-slow",2,1,"FORMING",now.getTime()+600000);
        javax.sql.DataSource original=jdbc.getDataSource();
        javax.sql.DataSource slow=new org.springframework.jdbc.datasource.AbstractDataSource(){
            public java.sql.Connection getConnection() throws java.sql.SQLException {
                java.sql.Connection c=original.getConnection();
                return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(proxy,method,args)->{
                    if(method.getName().equals("prepareStatement") && args[0] instanceof String && ((String)args[0]).contains("from ab_team t"))
                        args[0]=((String)args[0]).replace("where t.source=?","where SLEEP(4)=0 and t.source=?");
                    try{return method.invoke(c,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
            }
            public java.sql.Connection getConnection(String u,String p)throws java.sql.SQLException{return getConnection();}
        };
        V2TeamDiscoveryEngine engine=new V2TeamDiscoveryEngine();
        org.springframework.test.util.ReflectionTestUtils.setField(engine,"jdbc",new JdbcTemplate(slow));
        org.springframework.test.util.ReflectionTestUtils.setField(engine,"marketing",org.springframework.test.util.ReflectionTestUtils.getField(discovery,"marketing"));
        long start=System.nanoTime();
        try{engine.list(request);fail("Slow query should time out");}catch(org.springframework.dao.QueryTimeoutException expected){
            long elapsed=(System.nanoTime()-start)/1000000;assertTrue("elapsed="+elapsed,elapsed>=1000 && elapsed<3800);
        }
    }
    static List<String> ids(TeamDiscoveryPage p){List<String> ids=new ArrayList<>();for(TeamDiscoveryItem t:p.getItems())ids.add(t.getTeamId());return ids;}
}
