package cn.bugstack.domain.activity.service.discount.impl;

import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.AbstractDiscountCalculateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import cn.bugstack.domain.activity.service.discount.PricePolicy;
@Slf4j
@Service("ZK")
public class ZKCalculateService extends AbstractDiscountCalculateService {
    @Override
    protected BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount) {
        String marketExpr = groupBuyDiscount.getMarketExpr();
        BigDecimal rate = PricePolicy.nonNegative(marketExpr);
        if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0) PricePolicy.fail();
        BigDecimal deductionPrice = originalPrice.multiply(rate);

        return deductionPrice;
    }
}
