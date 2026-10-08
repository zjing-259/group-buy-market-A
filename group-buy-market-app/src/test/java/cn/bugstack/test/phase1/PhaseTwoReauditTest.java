package cn.bugstack.test.phase1;

import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.*;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.types.exception.AppException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import javax.annotation.Resource;
import java.util.Date;
import static org.junit.Assert.*;

/** Replay of committed facts must not depend on later admission-policy changes. */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PhaseOneTradeTest.Config.class)
public class PhaseTwoReauditTest {
    @Resource private JdbcTemplate jdbc;
    @Resource private ITradeLockOrderService lockService;
    @Resource private ITradeSettlementOrderService settlement;
    @Resource private DCCService dccService;
    private Object previousBlacklist;

    @Before public void seed() {
        PhaseOneTradeTest fixture = new PhaseOneTradeTest();
        ReflectionTestUtils.setField(fixture, "jdbc", jdbc);
        fixture.seed();
        previousBlacklist = ReflectionTestUtils.getField(dccService, "scBlacklist");
        ReflectionTestUtils.setField(dccService, "scBlacklist", "blockedblocked");
    }

    @After public void restorePolicy() {
        ReflectionTestUtils.setField(dccService, "scBlacklist", previousBlacklist);
    }

    private TradePaySuccessEntity payment(Date paid) {
        return TradePaySuccessEntity.builder().userId("u").source("s01").channel("c01")
                .outTradeNo("policyReplay").outTradeTime(paid).build();
    }

    private Date commitPayment() throws Exception {
        lockService.lockMarketPayOrder(UserEntity.builder().userId("u").build(),
                PayActivityEntity.builder().activityId(900001L).build(),
                PayDiscountEntity.builder().source("s01").channel("c01").goodsId("testsku")
                        .outTradeNo("policyReplay").notifyUrl("http://127.0.0.1:1/never-called").build());
        Date paid = new Date();
        settlement.settlementMarketPayOrder(payment(paid));
        ReflectionTestUtils.setField(dccService, "scBlacklist", "s01c01");
        return paid;
    }

    @Test public void committedPaymentReplaySurvivesLaterChannelBlocking() throws Exception {
        Date paid = commitPayment();
        TradePaySettlementEntity replay = settlement.settlementMarketPayOrder(payment(paid));
        assertEquals("policyReplay", replay.getOutTradeNo());
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("select complete_count from group_buy_order", Integer.class));
        assertEquals(paid.getTime(), jdbc.queryForObject("select out_trade_time from group_buy_order_list", Date.class).getTime());
    }

    @Test public void committedPaymentConflictRemainsConflictAfterChannelBlocking() throws Exception {
        Date paid = commitPayment();
        try {
            settlement.settlementMarketPayOrder(payment(new Date(paid.getTime() - 1)));
            fail("Expected payment fact conflict");
        } catch (AppException e) {
            assertEquals("E0113", e.getCode());
        }
    }
}
