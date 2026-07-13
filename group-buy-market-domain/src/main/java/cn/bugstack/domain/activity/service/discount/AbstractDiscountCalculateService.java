package cn.bugstack.domain.activity.service.discount;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.DiscountTypeEnum;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.math.BigDecimal;
@Slf4j
public abstract class AbstractDiscountCalculateService implements IDiscountCalculateService{
    @Resource
    private IActivityRepository repository;

    @Override
    public BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount){
        if(DiscountTypeEnum.TAG.equals(groupBuyDiscount.getDiscountType())){
            boolean isCrowRange = filterTagId(userId, groupBuyDiscount.getTagId());
            if(!isCrowRange) {

                log.info("折扣优惠计算拦截 userId:{}", userId);

                return originalPrice;
            }
        }

        return doCalculate(originalPrice, groupBuyDiscount);
    }

    protected abstract BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount);

    //过滤人群标签
    private boolean filterTagId(String userId, String tagId) {
        return repository.isTagCrowRange(tagId, userId);
    }

    //防止倒贴钱
    protected BigDecimal ensureMinimumPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.01");
        }
        return price;
    }
}
