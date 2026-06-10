package cn.bugstack.types.design.framework.tree;

import lombok.Getter;
import lombok.Setter;

public abstract class AbstractMultiThreadStrategyRouter<T,D,R> implements StrategyHandler<T,D,R>, StrategyMapper<T,D,R>{

    @Getter
    @Setter
    protected StrategyHandler<T, D, R> defaultStrategyHandler = StrategyHandler.DEFAULT;

    public R router(T requestParameter, D dynamicContext) throws Exception {
        StrategyHandler<T, D, R> strategyHandler = get(requestParameter, dynamicContext);
        if(null != strategyHandler) return strategyHandler.apply(requestParameter, dynamicContext);
        return defaultStrategyHandler.apply(requestParameter, dynamicContext);
    }

    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        mutiThread(requestParameter,dynamicContext);
        return doApply(requestParameter, dynamicContext);
    }

    //异步数据的加载
    protected abstract void mutiThread(T requestParameter, D dynamicContext) throws Exception;

    protected abstract R doApply(T requestParameter, D dynamicContext) throws Exception;


}
