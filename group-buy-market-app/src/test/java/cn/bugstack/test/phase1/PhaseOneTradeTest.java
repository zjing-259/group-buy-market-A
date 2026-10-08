package cn.bugstack.test.phase1;

import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.*;
import cn.bugstack.domain.trade.service.lock.TradeLockOrderService;
import cn.bugstack.domain.trade.service.settlement.TradeSettlementOrderService;
import cn.bugstack.infrastructure.adapter.repository.*;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.exception.AppException;
import cn.bugstack.trigger.http.MarketTradeController;
import cn.bugstack.api.dto.LockMarketPayOrderRequestDTO;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.redisson.api.RBitSet;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real MySQL transactions, isolated schema; no scheduler, real callback or production Redis. */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PhaseOneTradeTest.Config.class)
public class PhaseOneTradeTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan("cn.bugstack.infrastructure.dao")
    @ComponentScan({"cn.bugstack.domain.activity.service", "cn.bugstack.domain.trade.service.settlement.filter",
            "cn.bugstack.domain.trade.service.settlement.factory"})
    @Import({TradeLockOrderService.class, TradeSettlementOrderService.class, TradeRepository.class,
            ActivityRepository.class, MarketTradeController.class})
    public static class Config {
        @Bean(destroyMethod = "close") public HikariDataSource dataSource() {
            String password = System.getenv("PHASE1_DB_PASSWORD");
            if (password == null) throw new IllegalStateException("Set PHASE1_DB_PASSWORD for the isolated test database");
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:mysql://127.0.0.1:13306/group_buy_market_phase1_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");
            ds.setUsername("root"); ds.setPassword(password); ds.setMaximumPoolSize(25);
            return ds;
        }
        @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean bean = new SqlSessionFactoryBean(); bean.setDataSource(ds);
            bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mybatis/mapper/*.xml"));
            return bean.getObject();
        }
        @Bean public PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean public JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean public DCCService dccService() {
            DCCService dcc = new DCCService();
            ReflectionTestUtils.setField(dcc, "cutRange", "100");
            ReflectionTestUtils.setField(dcc, "scBlacklist", "blockedblocked");
            return dcc;
        }
        @Bean public IRedisService redisService() {
            IRedisService redis = mock(IRedisService.class);
            RBitSet bitmap = mock(RBitSet.class);
            when(bitmap.isExists()).thenReturn(true);
            when(redis.getBitSet(anyString())).thenReturn(bitmap);
            return redis;
        }
        @Bean public ITradePort tradePort() { return mock(ITradePort.class); }
        @Bean(destroyMethod = "shutdownNow") public ThreadPoolExecutor threadPoolExecutor() {
            return new ThreadPoolExecutor(4, 4, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        }
    }

    @Resource private JdbcTemplate jdbc;
    @Resource private ITradeLockOrderService lockService;
    @Resource private ITradeSettlementOrderService settlement;
    @Resource private IIndexGroupBuyMarketService index;
    @Resource private ITradeRepository repository;
    @Resource private PlatformTransactionManager transactionManager;
    @Resource private MarketTradeController controller;

    @Before public void seed() {
        assertEquals("group_buy_market_phase1_test", jdbc.queryForObject("select database()", String.class));
        for (String table : Arrays.asList("notify_task", "group_buy_order_list", "group_buy_order", "trade_request_guard",
                "trade_quota_guard", "sc_sku_activity", "sku", "group_buy_activity", "group_buy_discount",
                "crowd_tags_detail", "crowd_tags")) jdbc.update("delete from " + table);
        jdbc.update("insert into group_buy_discount(discount_id,discount_name,discount_desc,discount_type,market_plan,market_expr) values('90000001','test','test',0,'ZJ','20')");
        jdbc.update("insert into group_buy_activity(activity_id,activity_name,discount_id,take_limit_count,target,valid_time,status,start_time,end_time) values(900001,'test','90000001',10,3,60,1,now()-interval 1 day,now()+interval 1 day)");
        jdbc.update("insert into sku(source,channel,goods_id,goods_name,original_price) values('s01','c01','testsku','test',100)");
        jdbc.update("insert into sc_sku_activity(source,channel,goods_id,activity_id) values('s01','c01','testsku',900001)");
    }

    private PayDiscountEntity input(String out) {
        return PayDiscountEntity.builder().source("s01").channel("c01").goodsId("testsku").outTradeNo(out)
                .notifyUrl("http://127.0.0.1:1/never-called").originalPrice(new BigDecimal("999"))
                .deductionPrice(new BigDecimal("999")).payPrice(BigDecimal.ZERO).build();
    }
    private MarketPayOrderEntity lock(String user, String out, String team) throws Exception {
        return lockService.lockMarketPayOrder(UserEntity.builder().userId(user).build(),
                PayActivityEntity.builder().activityId(900001L).teamId(team).targetCount(999).build(), input(out));
    }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private void rejected(String code, Callable<?> task) throws Exception {
        try { task.call(); fail("Expected " + code); } catch (AppException e) { assertEquals(code, e.getCode()); }
    }
    private List<Object> race(int n, IntFunction<Callable<?>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n), start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i=0;i<n;i++) { final int id=i; futures.add(pool.submit(() -> {
                ready.countDown(); start.await();
                try { return task.apply(id).call(); } catch (Exception e) { return e; }
            })); }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<Object> values = new ArrayList<>();
            for (Future<Object> future : futures) values.add(future.get(45, TimeUnit.SECONDS));
            return values;
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test public void amountSnapshotAndUnpaidTime() throws Exception {
        MarketPayOrderEntity result=lock("u1","order1",null);
        Map<String,Object> detail=jdbc.queryForMap("select * from group_buy_order_list where order_id=?",result.getOrderId());
        assertEquals(new BigDecimal("100.00"),detail.get("original_price"));
        assertEquals(new BigDecimal("20.00"),detail.get("deduction_price"));
        assertEquals(new BigDecimal("80.00"),detail.get("pay_price"));
        assertNull(detail.get("out_trade_time"));
        assertEquals(new BigDecimal("100.00"),jdbc.queryForObject("select original_price from group_buy_order",BigDecimal.class));
        assertEquals(Integer.valueOf(3),jdbc.queryForObject("select target_count from group_buy_order",Integer.class));
    }

    @Test public void oneHundredIdenticalRequestsOnlyReserveOnce() throws Exception {
        List<Object> values=race(100,i -> () -> lock("same","sameorder",null));
        Set<String> ids=new HashSet<>();
        for(Object value:values) { assertTrue(String.valueOf(value),value instanceof MarketPayOrderEntity); ids.add(((MarketPayOrderEntity)value).getOrderId()); }
        assertEquals(1,ids.size()); assertEquals(1,count("group_buy_order")); assertEquals(1,count("group_buy_order_list"));
        assertEquals(Integer.valueOf(1),jdbc.queryForObject("select lock_count from group_buy_order",Integer.class));
    }

    @Test public void quotaRaceAndDifferentUsers() throws Exception {
        jdbc.update("update group_buy_activity set take_limit_count=1");
        List<Object> values=race(20,i -> () -> lock("quota","quota"+i,null));
        int success=0;
        for(Object value:values) { if(value instanceof MarketPayOrderEntity) success++; else { assertTrue(String.valueOf(value),value instanceof AppException); assertEquals("E0103",((AppException)value).getCode()); } }
        assertEquals(1,success); assertEquals(1,count("group_buy_order")); assertEquals(1,count("group_buy_order_list"));
        lock("other","other",null); assertEquals(2,count("group_buy_order_list"));
    }

    @Test public void committedOrderReplaysAfterPaymentAndConfigurationChanges() throws Exception {
        MarketPayOrderEntity first=lock("u1","replay",null);
        jdbc.update("update group_buy_order_list set status=1,out_trade_time=now()");
        jdbc.update("update group_buy_activity set status=2,take_limit_count=0");
        jdbc.update("update group_buy_discount set market_expr='99'");
        MarketPayOrderEntity again=lock("u1","replay",null);
        assertEquals(first.getOrderId(),again.getOrderId()); assertEquals(new BigDecimal("20.00"),again.getDeductionPrice());
        assertEquals(1,count("group_buy_order_list"));
        rejected("E0107",()->lock("other","replay",null));
        rejected("E0107",()->lock("u1","replay",first.getTeamId()));
        PayDiscountEntity changed=input("replay"); changed.setGoodsId("otherSku");
        rejected("E0107",()->lockService.lockMarketPayOrder(UserEntity.builder().userId("u1").build(),PayActivityEntity.builder().activityId(900001L).build(),changed));
        changed.setGoodsId("testsku"); changed.setNotifyUrl("http://changed");
        rejected("E0107",()->lockService.lockMarketPayOrder(UserEntity.builder().userId("u1").build(),PayActivityEntity.builder().activityId(900001L).build(),changed));
    }

    @Test public void requestValidationAndActivityIdentity() throws Exception {
        for(String invalid:Arrays.asList("", "   ","1234567890123"," trailing")) rejected("0002",()->lock("u",invalid,null));
        rejected("E0108",()->lockService.lockMarketPayOrder(UserEntity.builder().userId("u").build(),PayActivityEntity.builder().activityId(900002L).build(),input("wrongact")));
        assertEquals(0,count("group_buy_order_list")); assertEquals(0,count("group_buy_order"));
        rejected("E0108",()->index.indexMarketTrial(MarketProductEntity.builder().userId("u").source("s01").channel("c01").goodsId("testsku").activityId(900002L).build()));
    }

    @Test public void eligibilityIsEnforcedForDirectDomainCalls() throws Exception {
        jdbc.update("update group_buy_activity set tag_id='restricted',tag_scope='0,2'");
        assertTrue(index.indexMarketTrial(MarketProductEntity.builder().userId("u").source("s01").channel("c01").goodsId("testsku").build()).getIsVisible());
        rejected("E0110",()->lock("u","denied",null)); assertEquals(0,count("group_buy_order_list"));
        jdbc.update("update group_buy_activity set tag_scope='1,0'");
        lock("u","allowed",null); assertEquals(1,count("group_buy_order_list"));
    }

    @Test public void teamsRejectWrongIdentityExpiredAndFinished() throws Exception {
        String team=lock("leader","leader",null).getTeamId();
        rejected("E0109",()->lock("member","missing","00000000"));
        for(String change:Arrays.asList("activity_id=999999", "goods_id='other'", "source='other'", "channel='other'", "notify_url='other'", "status=1", "valid_end_time=now()-interval 1 minute")) {
            Map<String,Object> old=jdbc.queryForMap("select * from group_buy_order where team_id=?",team);
            jdbc.update("update group_buy_order set "+change+" where team_id=?",team);
            rejected("E0109",()->lock("member","badjoin",team));
            jdbc.update("update group_buy_order set activity_id=?,goods_id=?,source=?,channel=?,notify_url=?,status=?,valid_end_time=? where team_id=?",
                    old.get("activity_id"),old.get("goods_id"),old.get("source"),old.get("channel"),old.get("notify_url"),old.get("status"),old.get("valid_end_time"),team);
        }
        assertEquals(1,count("group_buy_order_list"));
        assertEquals(Integer.valueOf(1),jdbc.queryForObject("select lock_count from group_buy_order",Integer.class));
    }

    @Test public void lastSeatRaceAndRollbackAfterReservation() throws Exception {
        jdbc.update("update group_buy_activity set target=2");
        String team=lock("leader","leader",null).getTeamId();
        List<Object> values=race(15,i -> () -> lock("member"+i,"seat"+i,team));
        assertEquals(1,values.stream().filter(v->v instanceof MarketPayOrderEntity).count());
        for(Object v:values) if(v instanceof Exception) { assertTrue(v.toString(),v instanceof AppException); assertEquals("E0109",((AppException)v).getCode()); }
        assertEquals(2,count("group_buy_order_list"));
        assertEquals(Integer.valueOf(2),jdbc.queryForObject("select lock_count from group_buy_order",Integer.class));
        // Roll back a complete domain invocation after it has inserted its order and team.
        try { new TransactionTemplate(transactionManager).execute(status->{
            try { lock("rollback","rollback",null); } catch(Exception e) { throw new RuntimeException(e); }
            throw new IllegalStateException("injected after insert");
        }); fail(); } catch(IllegalStateException expected) { }
        assertEquals(1,count("group_buy_order")); assertEquals(2,count("group_buy_order_list"));
    }

    private TradePaySuccessEntity payment(String user,String out,String source,String channel,Date when) {
        return TradePaySuccessEntity.builder().userId(user).outTradeNo(out).source(source).channel(channel).outTradeTime(when).build();
    }
    @Test public void settlementChannelAndPaymentTimeBoundaries() throws Exception {
        String team=lock("u","pay",null).getTeamId();
        Date created=jdbc.queryForObject("select create_time from group_buy_order_list",Date.class);
        Date end=jdbc.queryForObject("select valid_end_time from group_buy_order",Date.class);
        rejected("E0104",()->settlement.settlementMarketPayOrder(payment("u","pay","other","c01",new Date())));
        rejected("E0112",()->settlement.settlementMarketPayOrder(payment("other","pay","s01","c01",new Date())));
        for(Date invalid:Arrays.asList(new Date(created.getTime()-1000),end,new Date(end.getTime()+1000),new Date(System.currentTimeMillis()+60000)))
            rejected("E0106",()->settlement.settlementMarketPayOrder(payment("u","pay","s01","c01",invalid)));
        assertEquals(Integer.valueOf(0),jdbc.queryForObject("select complete_count from group_buy_order",Integer.class));
        // A delayed notification is accepted if its payment fact was within the team's interval.
        jdbc.update("update group_buy_order_list set create_time=now()-interval 10 minute");
        jdbc.update("update group_buy_order set valid_start_time=now()-interval 10 minute,valid_end_time=now()-interval 1 minute");
        settlement.settlementMarketPayOrder(payment("u","pay","s01","c01",new Date(System.currentTimeMillis()-120000)));
        assertEquals(Integer.valueOf(1),jdbc.queryForObject("select complete_count from group_buy_order where team_id=?",Integer.class,team));
    }

    @Test public void sameExternalNumberInDifferentChannelsCannotCrossSettle() throws Exception {
        lock("u","shared",null);
        jdbc.update("insert into sc_sku_activity(source,channel,goods_id,activity_id) values('s02','c02','testsku',900001)");
        PayDiscountEntity other=input("shared"); other.setSource("s02"); other.setChannel("c02");
        MarketPayOrderEntity second=lockService.lockMarketPayOrder(UserEntity.builder().userId("u").build(),PayActivityEntity.builder().activityId(900001L).build(),other);
        settlement.settlementMarketPayOrder(payment("u","shared","s02","c02",new Date()));
        assertEquals(Integer.valueOf(1),jdbc.queryForObject("select status from group_buy_order_list where order_id=?",Integer.class,second.getOrderId()));
        assertEquals(Integer.valueOf(0),jdbc.queryForObject("select status from group_buy_order_list where source='s01'",Integer.class));
    }

    @Test public void sharedConfigurationLocksHoldUntilCommit() throws Exception {
        ExecutorService pool=Executors.newSingleThreadExecutor();
        final Future<?>[] update=new Future<?>[1];
        try {
            new TransactionTemplate(transactionManager).execute(status->{
                repository.lockActivityConfiguration("s01","c01","testsku",900001L);
                CountDownLatch started=new CountDownLatch(1);
                update[0]=pool.submit(()->{ started.countDown(); jdbc.update("update group_buy_activity set target=4 where activity_id=900001"); });
                try { assertTrue(started.await(5,TimeUnit.SECONDS)); update[0].get(200,TimeUnit.MILLISECONDS); fail("configuration must be locked"); }
                catch(TimeoutException expected) { } catch(Exception e) { throw new RuntimeException(e); }
                return null;
            });
            update[0].get(5,TimeUnit.SECONDS);
            lock("u","newconfig",null);
            assertEquals(Integer.valueOf(4),jdbc.queryForObject("select target_count from group_buy_order",Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test public void httpEntryUsesSameValidationAndIdempotency() {
        assertEquals("0002",controller.lockMarketPayOrder(null).getCode());
        LockMarketPayOrderRequestDTO request=new LockMarketPayOrderRequestDTO();
        request.setUserId("http"); request.setSource("s01"); request.setChannel("c01"); request.setGoodsId("testsku");
        request.setActivityId(900001L); request.setNotifyUrl("http://127.0.0.1:1/never-called"); request.setOutTradeNo("http1");
        assertEquals("0000",controller.lockMarketPayOrder(request).getCode());
        request.setGoodsId("different"); assertEquals("E0107",controller.lockMarketPayOrder(request).getCode());
        request.setOutTradeNo(" "); assertEquals("0002",controller.lockMarketPayOrder(request).getCode());
        assertEquals(1,count("group_buy_order_list"));
    }

    @Test public void priceRoundingIsIdenticalInTrialAndDatabase() throws Exception {
        String[][] plans={{"ZJ","0.005","100.00"},{"MJ","100,0.005","100.00"},{"ZK","0.33333","33.33"},{"N","1.995","2.00"}};
        for(int i=0;i<plans.length;i++) {
            jdbc.update("update group_buy_discount set market_plan=?,market_expr=?",plans[i][0],plans[i][1]);
            MarketPayOrderEntity order=lock("rounding","round"+i,null);
            BigDecimal price=jdbc.queryForObject("select pay_price from group_buy_order_list where order_id=?",BigDecimal.class,order.getOrderId());
            assertEquals(new BigDecimal(plans[i][2]),price);
            assertEquals(order.getPayPrice(),price);
            assertEquals(new BigDecimal("100.00"),price.add(order.getDeductionPrice()));
        }
        jdbc.update("update group_buy_discount set market_plan='N',market_expr='-1'");
        rejected("E0111",()->lock("rounding","negative",null));
        jdbc.update("update sku set original_price=1000000");
        rejected("E0111",()->lock("rounding","overflow",null));
        assertEquals(4,count("group_buy_order_list")); assertEquals(4,count("group_buy_order"));
    }

    @Test public void legacyIdentityIsNotGuessedAndLongUserIdsAreAccepted() throws Exception {
        MarketPayOrderEntity legacy=lock("legacy","legacy",null);
        jdbc.update("update group_buy_order_list set request_team_id=null,notify_url=null,pay_price=null");
        jdbc.update("update group_buy_order set goods_id=null");
        rejected("E0107",()->lock("legacy","legacy",null));
        rejected("E0109",()->lock("joiner","joinlegacy",legacy.getTeamId()));
        String user=String.join("",Collections.nCopies(64,"u"));
        lock(user,"longuser",null); assertEquals(2,count("group_buy_order_list"));
    }
}
