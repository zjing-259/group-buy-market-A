package cn.bugstack.domain.activity.service.discount.impl;

import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.AbstractDiscountCalculateService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import cn.bugstack.domain.activity.service.discount.AbstractDiscountCalculateService;

import java.math.BigDecimal;

@Slf4j
@Service("MJ")
public class MJCalculateService extends AbstractDiscountCalculateService {
    @Override
    protected BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount) {
        String marketExpr = groupBuyDiscount.getMarketExpr();
        String[] split = marketExpr.split(Constants.SPLIT);
        //添加检验，满减类型必须有两个参数。
        if (split.length != 2) {
            throw new IllegalArgumentException("满减表达式格式错误: " + marketExpr);
        }
        //将两个值设为x,y
        BigDecimal x = new BigDecimal(split[0]);
        BigDecimal y = new BigDecimal(split[1]);

        if(originalPrice.compareTo(x) < 0)   return originalPrice;

        BigDecimal deductionPrice= originalPrice.subtract(y);

        return ensureMinimumPrice(deductionPrice);
    }
}
