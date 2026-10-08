package cn.bugstack.domain.activity.service.discount.impl;

import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.AbstractDiscountCalculateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import cn.bugstack.domain.activity.service.discount.PricePolicy;
@Slf4j
@Service("N")
public class NCalculateService extends AbstractDiscountCalculateService {

    @Override
    protected BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount) {
        String marketExpr = groupBuyDiscount.getMarketExpr();

        BigDecimal price = PricePolicy.nonNegative(marketExpr);
        if (price.signum() == 0) PricePolicy.fail();
        return price;
    }
}
