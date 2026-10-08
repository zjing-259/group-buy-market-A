package cn.bugstack.domain.activity.service.discount.impl;

import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.AbstractDiscountCalculateService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import cn.bugstack.domain.activity.service.discount.AbstractDiscountCalculateService;

import java.math.BigDecimal;
import cn.bugstack.domain.activity.service.discount.PricePolicy;

@Slf4j
@Service("MJ")
public class MJCalculateService extends AbstractDiscountCalculateService {
    @Override
    protected BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount) {
        String marketExpr = groupBuyDiscount.getMarketExpr();
        String[] split = marketExpr == null ? new String[0] : marketExpr.split(Constants.SPLIT, -1);
        //添加检验，满减类型必须有两个参数。
        if (split.length != 2) {
            PricePolicy.fail();
        }
        //将两个值设为x,y
        BigDecimal x = PricePolicy.nonNegative(split[0]);
        BigDecimal y = PricePolicy.nonNegative(split[1]);

        if (x.signum() <= 0 || y.compareTo(x) > 0) PricePolicy.fail();
        if(originalPrice.compareTo(x) < 0)   return originalPrice;

        BigDecimal deductionPrice= originalPrice.subtract(y);

        return deductionPrice;
    }
}
