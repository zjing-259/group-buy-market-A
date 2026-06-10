package cn.bugstack.domain.activity.service.trial.node;

import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.service.trial.AbstractGroupBuyMarketSupport;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.domain.activity.service.trial.thread.QueryGroupBuyActivityDiscountVOThreadTask;
import cn.bugstack.domain.activity.service.trial.thread.QuerySkuVOFromDBThreadTask;
import cn.bugstack.types.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.*;

@Slf4j
@Service
public class MarketNode extends AbstractGroupBuyMarketSupport<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> {

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    @Resource
    private EndNode endNode;

    @Override
    protected void mutiThread(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        QueryGroupBuyActivityDiscountVOThreadTask queryGroupBuyActivityDiscountVOThreadTask = new QueryGroupBuyActivityDiscountVOThreadTask(requestParameter.getSource(), requestParameter.getChannel(), repository);
        FutureTask<GroupBuyActivityDiscountVO> groupBuyActivityDiscountVOFutureTask = new FutureTask<>(queryGroupBuyActivityDiscountVOThreadTask);
        //放入线程池
        threadPoolExecutor.execute(groupBuyActivityDiscountVOFutureTask);

        QuerySkuVOFromDBThreadTask querySkuVOFromDBThreadTask = new QuerySkuVOFromDBThreadTask(requestParameter.getGoodsId(), repository);
        FutureTask<SkuVO> skuVOFutureTask = new FutureTask<>(querySkuVOFromDBThreadTask);
        //放入线程池
        threadPoolExecutor.execute(skuVOFutureTask);
        //配置上下文
        //dynamicContext.setGroupBuyActivityDiscountVO(groupBuyActivityDiscountVOFutureTask.get(timeout, TimeUnit.MILLISECONDS));
        //dynamicContext.setSkuVO(skuVOFutureTask.get(timeout, TimeUnit.MILLISECONDS));
        try {
            //配置上下文 - 增加超时时间到5秒
            GroupBuyActivityDiscountVO activityDiscountVO = groupBuyActivityDiscountVOFutureTask.get(timeout, TimeUnit.SECONDS);
            SkuVO skuVO = skuVOFutureTask.get(timeout, TimeUnit.SECONDS);

            if (activityDiscountVO != null) {
                dynamicContext.setGroupBuyActivityDiscountVO(activityDiscountVO);
                log.info("查询到团购活动信息: {}", activityDiscountVO.getActivityName());
            } else {
                log.warn("未查询到团购活动信息, source: {}, channel: {}", requestParameter.getSource(), requestParameter.getChannel());
            }

            if (skuVO != null) {
                dynamicContext.setSkuVO(skuVO);
                log.info("查询到商品信息: {}", skuVO.getGoodsName());
            } else {
                log.warn("未查询到商品信息, goodsId: {}", requestParameter.getGoodsId());
            }
        } catch (TimeoutException e) {
            log.error("异步任务执行超时", e);
            throw e;
        } catch (ExecutionException e) {
            log.error("异步任务执行异常", e);
            throw e;
        }
    }

    @Override
    public TrialBalanceEntity doApply(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> get(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return endNode;
    }
}
