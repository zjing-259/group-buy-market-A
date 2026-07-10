package cn.bugstack.types.design.framework.link.modle1;

public interface ILogicLink<T, D, R> extends ILogicChainArmory<T, D, R> {
    R apply(T requestParameter, D dynamicContext) throws Exception;

    ILogicLink<T, D, R> appendNext(ILogicLink<T, D, R> next);
}
