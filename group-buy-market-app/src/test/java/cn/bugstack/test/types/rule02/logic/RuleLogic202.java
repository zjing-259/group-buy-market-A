package cn.bugstack.test.types.rule02.logic;

import cn.bugstack.test.types.rule02.factory.Rule02TradeRuleFactory;
import cn.bugstack.types.design.framework.link.modle2.handler.ILogicHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RuleLogic202 implements ILogicHandler<String, Rule02TradeRuleFactory.DynamicContext, XxxResponse> {


    @Override
    public XxxResponse apply(String requestParameter, Rule02TradeRuleFactory.DynamicContext dynamicContext) throws Exception {

        log.info("link modle02 RuleLogic202");

        return new XxxResponse("hello!");
    }
}
