package cn.bugstack.domain.trade.service.lock.factory;

import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.lock.filter.ActivityUsabilityRuleFilter;
import cn.bugstack.domain.trade.service.lock.filter.UserTakeLimitRuleFilter;
import cn.bugstack.types.design.framework.link.model2.LinkArmory;
import cn.bugstack.types.design.framework.link.model2.chain.BusinessLinkedList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

//对责任链进行编排
@Slf4j
@Service
public class TradeLockRuleFilterFactory {

    @Bean("tradeRuleFilter")
    public BusinessLinkedList<TradeLockRuleCommandEntity, DynamicContext, TradeLockRuleFilterBackEntity> tradeRuleFilter(ActivityUsabilityRuleFilter activityUsabilityRuleFilter, UserTakeLimitRuleFilter userTakeLimitRuleFilter) {

        LinkArmory<TradeLockRuleCommandEntity, DynamicContext, TradeLockRuleFilterBackEntity> linkArmory =
                new LinkArmory<>("交易规则过滤链", activityUsabilityRuleFilter, userTakeLimitRuleFilter);

        return linkArmory.getLogicLink();
    }



    //一些不方便维护的都可以放到上下文中
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext{
        private GroupBuyActivityEntity groupBuyActivityEntity;
    }

}
