package cn.bugstack.test.types.rule01.logic;

import cn.bugstack.types.design.framework.link.model1.AbstractLogicLink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import cn.bugstack.test.types.rule02.factory.Rule02TradeRuleFactory;

@Slf4j
@Service
public class RuleLogic101 extends AbstractLogicLink<String, Rule02TradeRuleFactory.DynamicContext, String> {

    @Override
    public String apply(String requestParameter, Rule02TradeRuleFactory.DynamicContext dynamicContext) throws Exception {

        log.info("link modle01 RuleLogic101");

        return next(requestParameter, dynamicContext);
    }
}
