package cn.bugstack.domain.activity.service.trial.node;

import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.service.discount.IDiscountCalculateService;
import cn.bugstack.domain.activity.service.trial.AbstractGroupBuyMarketSupport;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.domain.activity.service.trial.thread.QueryGroupBuyActivityDiscountVOThreadTask;
import cn.bugstack.domain.activity.service.trial.thread.QuerySkuVOFromDBThreadTask;
import cn.bugstack.types.design.framework.tree.StrategyHandler;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.Map;

@Slf4j
@Service
public class MarketNode extends AbstractGroupBuyMarketSupport<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> {

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    @Resource
    private EndNode endNode;
    @Resource
    private ErrorNode errorNode;
    @Resource
    private TagNode tagNode;
    @Resource
    private Map<String, IDiscountCalculateService> discountCalculateServiceMap;

    @Override
    protected void multiThread(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // Locking calls must keep the configuration reads on the transaction's connection.
        // Otherwise concurrent requests can exhaust the pool while waiting for their own trial tasks.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            cn.bugstack.domain.activity.model.valobj.SCSkuActivityVO mapping = repository.querySCSkuActivityBySCGoodsId(
                    requestParameter.getSource(), requestParameter.getChannel(), requestParameter.getGoodsId());
            dynamicContext.setGroupBuyActivityDiscountVO(mapping == null ? null : repository.queryGroupBuyActivityDiscountVO(mapping.getActivityId()));
            dynamicContext.setSkuVO(repository.querySkuByGoodsId(requestParameter.getGoodsId()));
            return;
        }
        long deadline = System.nanoTime() + trialTimeoutNanos;
        QueryGroupBuyActivityDiscountVOThreadTask queryGroupBuyActivityDiscountVOThreadTask = new QueryGroupBuyActivityDiscountVOThreadTask(requestParameter.getSource(), requestParameter.getChannel(), requestParameter.getGoodsId()  , repository);
        FutureTask<GroupBuyActivityDiscountVO> groupBuyActivityDiscountVOFutureTask = new FutureTask<>(queryGroupBuyActivityDiscountVOThreadTask);
        QuerySkuVOFromDBThreadTask querySkuVOFromDBThreadTask = new QuerySkuVOFromDBThreadTask(requestParameter.getGoodsId(), repository);
        FutureTask<SkuVO> skuVOFutureTask = new FutureTask<>(querySkuVOFromDBThreadTask);
        try {
            threadPoolExecutor.execute(groupBuyActivityDiscountVOFutureTask);
            threadPoolExecutor.execute(skuVOFutureTask);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException("Trial lookup deadline exceeded");
            GroupBuyActivityDiscountVO activityDiscountVO = groupBuyActivityDiscountVOFutureTask.get(remaining, TimeUnit.NANOSECONDS);
            remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException("Trial lookup deadline exceeded");
            SkuVO skuVO = skuVOFutureTask.get(remaining, TimeUnit.NANOSECONDS);

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
        } catch (TimeoutException | ExecutionException | InterruptedException | RejectedExecutionException e) {
            groupBuyActivityDiscountVOFutureTask.cancel(true);
            skuVOFutureTask.cancel(true);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("试算配置查询未在预算内完成", e);
            throw e;
        }
    }

    @Override
    public TrialBalanceEntity doApply(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
        //打印日志
        log.info("拼团商品查询试算服务-MarketNode userId:{} requestParameter:{}", requestParameter.getUserId(), JSON.toJSONString(requestParameter));
        //获取信息
        GroupBuyActivityDiscountVO groupBuyActivityDiscountVO = dynamicContext.getGroupBuyActivityDiscountVO();
        if(null == groupBuyActivityDiscountVO) return router(requestParameter, dynamicContext);
        if (requestParameter.getActivityId() != null && !requestParameter.getActivityId().equals(groupBuyActivityDiscountVO.getActivityId())) {
            throw new AppException(ResponseCode.ACTIVITY_MISMATCH);
        }

        GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount = groupBuyActivityDiscountVO.getGroupBuyDiscount();
        SkuVO skuVO = dynamicContext.getSkuVO();
        if(null == skuVO || null == groupBuyDiscount) return router(requestParameter, dynamicContext);

        //拼团优惠试算
        //获取计算服务
        IDiscountCalculateService discountCalculateService = discountCalculateServiceMap.get(groupBuyDiscount.getMarketPlan());
        if(null == discountCalculateService)   throw new AppException(ResponseCode.E0001.getCode(), ResponseCode.E0001.getInfo());

        //优惠试算
        BigDecimal payPrice = discountCalculateService.calculate(requestParameter.getUserId(), skuVO.getOriginalPrice(), groupBuyDiscount);
        //优惠了多少钱
        dynamicContext.setDeductionPrice(skuVO.getOriginalPrice().subtract(payPrice));
        dynamicContext.setPayPrice(payPrice);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> get(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
        if(null == dynamicContext.getGroupBuyActivityDiscountVO() || null == dynamicContext.getSkuVO() || null == dynamicContext.getDeductionPrice()){
            return errorNode;
        }

        return tagNode;
    }
}
