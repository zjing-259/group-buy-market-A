package cn.bugstack.test.types.rule01.factory;

import cn.bugstack.test.types.rule01.logic.RuleLogic101;
import cn.bugstack.test.types.rule01.logic.RuleLogic102;
import cn.bugstack.types.design.framework.link.model1.ILogicLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import cn.bugstack.test.types.rule02.factory.Rule02TradeRuleFactory;
import javax.annotation.Resource;

@Service
public class Rule01TradeRuleFactory {
    @Resource
    private RuleLogic101 ruleLogic101;
    @Resource
    private RuleLogic102 ruleLogic102;

    //将ruleLogic102接在ruleLogic101后面，并且不接收返回的ruleLogic102。
    public ILogicLink<String, Rule02TradeRuleFactory.DynamicContext, String> openLogicLink(){
        ruleLogic101.appendNext(ruleLogic102);
        return ruleLogic101;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DDynamicContext{
        private String age;
    }

}
