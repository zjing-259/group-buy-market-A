package cn.bugstack.test.phase1;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.types.common.CutRangePolicy;
import cn.bugstack.types.exception.AppException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PhaseOneTradeTest.Config.class)
public class PhaseFourAuxTest {
    @Resource private JdbcTemplate jdbc;
    @Resource private IActivityRepository repository;
    @Resource private IIndexGroupBuyMarketService index;
    @Resource private ITradeLockOrderService lockService;
    @Resource private DCCService dcc;

    @Before public void seed() {
        PhaseOneTradeTest fixture=new PhaseOneTradeTest();
        ReflectionTestUtils.setField(fixture,"jdbc",jdbc);
        fixture.seed();
    }

    private TrialBalanceEntity trial(String user) throws Exception {
        return index.indexMarketTrial(MarketProductEntity.builder().userId(user).source("s01").channel("c01").goodsId("testsku").build());
    }
    private MarketPayOrderEntity lock(String user,String out,String team) throws Exception {
        return lockService.lockMarketPayOrder(UserEntity.builder().userId(user).build(),
                PayActivityEntity.builder().activityId(900001L).teamId(team).build(),
                PayDiscountEntity.builder().source("s01").channel("c01").goodsId("testsku")
                        .outTradeNo(out).notifyUrl("http://127.0.0.1:1/callback").build());
    }

    @Test public void exactMembershipRejectsMissingTagAndHashCollision() throws Exception {
        assertTrue(trial("open").getIsEnable());
        assertEquals(new BigDecimal("80.00"),trial("open").getPayPrice());
        jdbc.update("insert into crowd_tags(tag_id,tag_name,tag_desc,statistics) values('restricted','test','test',1)");
        jdbc.update("insert into crowd_tags_detail(tag_id,user_id) values('restricted','member72789')");
        jdbc.update("update group_buy_activity set tag_id='restricted',tag_scope='0,2'");
        jdbc.update("update group_buy_discount set discount_type=1,tag_id='restricted'");
        cn.bugstack.infrastructure.redis.RedissonService hasher=new cn.bugstack.infrastructure.redis.RedissonService();
        assertEquals(hasher.getIndexFromUserId("member72789"),hasher.getIndexFromUserId("member95446"));
        assertTrue(repository.isTagCrowRange("restricted","member72789"));
        assertFalse(repository.isTagCrowRange("restricted","member95446"));
        assertFalse(repository.isTagCrowRange("restricted","Member72789"));
        assertFalse(repository.isTagCrowRange("missing","member72789"));
        assertTrue(trial("member72789").getIsEnable());
        assertEquals(new BigDecimal("80.00"),trial("member72789").getPayPrice());
        TrialBalanceEntity outsider=trial("member95446");
        assertFalse(outsider.getIsEnable());
        assertEquals(new BigDecimal("100.00"),outsider.getPayPrice());
        try { lock("member95446","collision",null); fail(); }
        catch(AppException e) { assertEquals("E0110",e.getCode()); }
        assertEquals(Integer.valueOf(0),jdbc.queryForObject("select count(*) from group_buy_order_list",Integer.class));
        jdbc.update("delete from crowd_tags_detail where tag_id='restricted'");
        assertFalse(repository.isTagCrowRange("restricted","member72789"));
        assertFalse(trial("member72789").getIsEnable());
        assertEquals(new BigDecimal("100.00"),trial("member72789").getPayPrice());
        jdbc.update("update group_buy_activity set tag_id=null,tag_scope=null");
        jdbc.update("update group_buy_discount set discount_type=0,tag_id=null");
        assertTrue(trial("open").getIsEnable());
    }

    @Test public void invalidOrMissingTaggedDiscountNeverGrantsPromotion() throws Exception {
        jdbc.update("update group_buy_discount set discount_type=1,tag_id='missing'");
        assertEquals(new BigDecimal("100.00"),trial("u").getPayPrice());
        jdbc.update("update group_buy_discount set tag_id=null");
        assertEquals(new BigDecimal("100.00"),trial("u").getPayPrice());
    }

    @Test public void cutRangeHasExactPercentageAndInvalidValuesFailClosed() {
        Object prior=ReflectionTestUtils.getField(dcc,"cutRange");
        try {
            ReflectionTestUtils.setField(dcc,"cutRange","0");
            for(int i=0;i<200;i++) assertFalse(dcc.iscutRange("user"+i));
            ReflectionTestUtils.setField(dcc,"cutRange","100");
            for(int i=0;i<200;i++) assertTrue(dcc.iscutRange("user"+i));
            ReflectionTestUtils.setField(dcc,"cutRange","10");
            Set<Integer> accepted=new HashSet<>();
            for(int i=0;i<10000;i++) {
                String user="user"+i;
                int bucket=Math.floorMod(user.hashCode(),100);
                if(dcc.iscutRange(user)) accepted.add(bucket);
                assertEquals(bucket<10,dcc.iscutRange(user));
            }
            assertEquals(10,accepted.size());
            assertEquals(Integer.MIN_VALUE,"polygenelubricants".hashCode());
            assertEquals(Math.floorMod(Integer.MIN_VALUE,100)<10,dcc.iscutRange("polygenelubricants"));
            for(String invalid:Arrays.asList("-1","101","garbage","10.0"," 10","01","")) {
                try { CutRangePolicy.parse(invalid); fail("Accepted "+invalid); }
                catch(IllegalArgumentException expected) { }
                ReflectionTestUtils.setField(dcc,"cutRange",invalid);
                assertFalse(dcc.iscutRange("user1"));
            }
        } finally { ReflectionTestUtils.setField(dcc,"cutRange",prior); }
    }

    @Test public void ownerListDoesNotExposeCaseVariantUsersOrders() throws Exception {
        lock("CaseOwner", "privateOrder", null);
        assertEquals(1, index.queryInProgressUserGroupBuyOrderDetailList(900001L,"CaseOwner",10,0).size());
        List<UserGroupBuyOrderDetailEntity> other = index.queryInProgressUserGroupBuyOrderDetailList(900001L,"caseowner",10,0);
        assertTrue("Different exact user ID must not receive another user's private order: " +
                other.stream().map(UserGroupBuyOrderDetailEntity::getOutTradeNo).collect(Collectors.toList()), other.isEmpty());
    }

    @Test public void recommendationsDoNotTreatCaseVariantUserAsAlreadyJoined() throws Exception {
        String team = lock("CaseOwner", "privateOrder", null).getTeamId();
        List<UserGroupBuyOrderDetailEntity> other = index.queryInProgressUserGroupBuyOrderDetailList(900001L,"caseowner",0,10);
        assertTrue("Different exact user ID has not joined this team",
                other.stream().anyMatch(order -> team.equals(order.getTeamId())));
    }

    @Test public void ownFullTeamIsVisibleAndRecommendationsExcludeExpiredAndPrivateFacts() throws Exception {
        jdbc.update("update group_buy_activity set target=2");
        String full=lock("u1","full1",null).getTeamId();
        lock("u2","full2",full);
        String expired=lock("u3","expired",null).getTeamId();
        String open=lock("u4","openone",null).getTeamId();
        jdbc.update("update group_buy_order set valid_end_time=now()-interval 1 second where team_id=?",expired);
        List<UserGroupBuyOrderDetailEntity> own=index.queryInProgressUserGroupBuyOrderDetailList(900001L,"u1",10,0);
        assertEquals(1,own.size());
        assertEquals(full,own.get(0).getTeamId());
        assertEquals("u1",own.get(0).getUserId());
        assertEquals("full1",own.get(0).getOutTradeNo());
        assertEquals(Integer.valueOf(2),own.get(0).getLockCount());
        List<UserGroupBuyOrderDetailEntity> recommended=index.queryInProgressUserGroupBuyOrderDetailList(900001L,"visitor",0,10);
        assertEquals(Collections.singleton(open),recommended.stream().map(UserGroupBuyOrderDetailEntity::getTeamId).collect(Collectors.toSet()));
        assertNull(recommended.get(0).getUserId());
        assertNull(recommended.get(0).getOutTradeNo());
        assertTrue(index.queryInProgressUserGroupBuyOrderDetailList(900001L,"u4",0,10).stream().noneMatch(x->open.equals(x.getTeamId())));
        jdbc.update("update group_buy_activity set end_time=now()-interval 1 second");
        assertEquals(full,index.queryInProgressUserGroupBuyOrderDetailList(900001L,"u1",10,0).get(0).getTeamId());
    }
}
