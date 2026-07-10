package cn.bugstack.test.types;

import cn.bugstack.test.types.rule01.factory.Rule01TradeRuleFactory;
import cn.bugstack.test.types.rule02.factory.Rule02TradeRuleFactory;
import cn.bugstack.types.design.framework.link.modle1.ILogicLink;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class Link01Test {
    //注入责任链01
    @Resource
    private Rule01TradeRuleFactory rule01TradeRuleFactory;

    @Test
    public void test_modle01_01() throws Exception{
        //获取由工厂rule01TradeRuleFactory构建的责任链，其中有两个节点
        ILogicLink<String, Rule02TradeRuleFactory.DynamicContext, String> logicLink = rule01TradeRuleFactory.openLogicLink();
        String logic = logicLink.apply("123", new Rule02TradeRuleFactory.DynamicContext());

        log.info("测试结果：{}" , JSON.toJSONString(logic));
    }

}
