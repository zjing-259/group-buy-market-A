package cn.bugstack.domain.activity.service.trial.node;

import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.service.trial.AbstractGroupBuyMarketSupport;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.types.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class EndNode extends AbstractGroupBuyMarketSupport<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> {
    @Override
    public TrialBalanceEntity doApply(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
        GroupBuyActivityDiscountVO groupBuyActivityDiscountVO = dynamicContext.getGroupBuyActivityDiscountVO();
        SkuVO skuVO = dynamicContext.getSkuVO();

        // 空指针检查
        if (skuVO == null || groupBuyActivityDiscountVO == null) {
            log.warn("数据不完整 - skuVO: {}, groupBuyActivityDiscountVO: {}",
                    skuVO != null ? "存在" : "null",
                    groupBuyActivityDiscountVO != null ? "存在" : "null");
        }

        BigDecimal deductionPrice = dynamicContext.getDeductionPrice();

        //封装好并返回
        return TrialBalanceEntity.builder()
                .goodsId(skuVO != null ? skuVO.getGoodsId() : null)
                .goodsName(skuVO != null ? skuVO.getGoodsName() : null)
                .originalPrice(skuVO != null ? skuVO.getOriginalPrice() : null)
                .deductionPrice(deductionPrice)
                .targetCount(groupBuyActivityDiscountVO != null ? groupBuyActivityDiscountVO.getTarget() : null)
                .startTime(groupBuyActivityDiscountVO != null ? groupBuyActivityDiscountVO.getStartTime() : null)
                .endTime(groupBuyActivityDiscountVO != null ? groupBuyActivityDiscountVO.getEndTime() : null)
                .isVisible(false)
                .isEnable(false)
                .build();
    }

    @Override
    public StrategyHandler<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> get(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }
}
