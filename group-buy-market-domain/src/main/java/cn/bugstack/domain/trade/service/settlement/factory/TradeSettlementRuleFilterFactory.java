package cn.bugstack.domain.trade.service.settlement.factory;

import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.settlement.filter.EndRuleFilter;
import cn.bugstack.domain.trade.service.settlement.filter.OutTradeNoRuleFilter;
import cn.bugstack.domain.trade.service.settlement.filter.SCRuleFilter;
import cn.bugstack.domain.trade.service.settlement.filter.SettableRuleFilter;
import cn.bugstack.types.design.framework.link.model2.LinkArmory;
import cn.bugstack.types.design.framework.link.model2.chain.BusinessLinkedList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TradeSettlementRuleFilterFactory {

    @Bean("tradeSettlementRuleFilter")
    /* 将该方法的返回值注册为 Spring Bean，Bean 名称为 tradeSettlementRuleFilter。其他 Service 可以通过 @Resource 按名称注入这条责任链来使用。*/
    public BusinessLinkedList<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext,TradeSettlementRuleFilterBackEntity>
    tradeSettlementRuleFilter(SCRuleFilter sCRuleFilter, OutTradeNoRuleFilter outTradeNoRuleFilter, SettableRuleFilter settableRuleFilter, EndRuleFilter endRuleFilter) {
        //过滤顺序为 SCRuleFilter -> OutTradeNoRuleFilter -> SettableRuleFilter -> EndRuleFilter
        LinkArmory<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext,TradeSettlementRuleFilterBackEntity> linkArmory =
                new LinkArmory<>("交易结算规则过滤链", sCRuleFilter, outTradeNoRuleFilter, settableRuleFilter, endRuleFilter);

        return linkArmory.getLogicLink();
    }

    //一些不方便维护的都可以放到上下文中
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext{
        private MarketPayOrderEntity marketPayOrderEntity;
        private GroupBuyTeamEntity groupBuyTeamEntity;
    }
}
