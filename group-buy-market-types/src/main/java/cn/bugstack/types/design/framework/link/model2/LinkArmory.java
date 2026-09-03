package cn.bugstack.types.design.framework.link.model2;

import cn.bugstack.types.design.framework.link.model2.chain.BusinessLinkedList;
import cn.bugstack.types.design.framework.link.model2.handler.ILogicHandler;

//链的组装器：只需提供处理器集合，即可获得一条配置好的责任链（BusinessLinkedList ）。
public class LinkArmory<T, D, R> {
    private final BusinessLinkedList<T, D, R> logicLink;

    @SafeVarargs
    public LinkArmory(String linkName, ILogicHandler<T, D, R>...logicHandlers) {
        logicLink = new BusinessLinkedList<>(linkName);
        for(ILogicHandler logicHandler : logicHandlers){
            logicLink.add(logicHandler);
        }
    }

    public BusinessLinkedList<T, D, R> getLogicLink() { return logicLink; }
}
