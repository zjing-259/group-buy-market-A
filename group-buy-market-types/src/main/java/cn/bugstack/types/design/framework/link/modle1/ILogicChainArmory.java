package cn.bugstack.types.design.framework.link.modle1;

public interface ILogicChainArmory<T, D, R> {

    //获取责任链中紧接在当前节点之后的下一个处理节点。
    ILogicLink<T, D, R> next();

    //在当前节点之后追加一个新的处理节点，形成链式结构。
    ILogicLink<T, D, R> appendNext(ILogicLink<T, D, R> next);

}
