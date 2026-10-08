package cn.bugstack.test.phase1;

import cn.bugstack.domain.activity.model.valobj.*;
import cn.bugstack.domain.activity.service.discount.*;
import cn.bugstack.domain.activity.service.discount.impl.*;
import cn.bugstack.types.exception.AppException;
import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class PhaseOnePriceTest {
    private BigDecimal calculate(IDiscountCalculateService strategy, String original, String expression) {
        return strategy.calculate("test",new BigDecimal(original),GroupBuyActivityDiscountVO.GroupBuyDiscount.builder()
                .discountType(DiscountTypeEnum.BASE).marketExpr(expression).build());
    }
    private void invalid(IDiscountCalculateService strategy,String original,String expression) {
        try { calculate(strategy,original,expression); fail("Expected invalid price: "+original+", "+expression); }
        catch(AppException e) { assertEquals("E0111",e.getCode()); }
    }
    @Test public void validStrategiesAndRounding() {
        assertEquals(new BigDecimal("80.00"),calculate(new ZJCalculateService(),"100","20"));
        assertEquals(new BigDecimal("90.00"),calculate(new MJCalculateService(),"100","100,10"));
        assertEquals(new BigDecimal("90.00"),calculate(new MJCalculateService(),"90","100,10"));
        assertEquals(new BigDecimal("80.00"),calculate(new ZKCalculateService(),"100","0.8"));
        assertEquals(new BigDecimal("1.99"),calculate(new NCalculateService(),"100","1.99"));
        assertEquals(new BigDecimal("2.00"),calculate(new NCalculateService(),"100","1.995"));
        assertEquals(new BigDecimal("0.01"),calculate(new ZKCalculateService(),"0.01","0.1"));
        assertEquals(new BigDecimal("0.01"),calculate(new ZJCalculateService(),"100","100"));
        assertEquals(new BigDecimal("100.00"),calculate(new ZJCalculateService(),"100","0.005"));
    }
    @Test public void invalidStrategiesDoNotSilentlyClampBadConfiguration() {
        for(IDiscountCalculateService strategy:new IDiscountCalculateService[]{new ZJCalculateService(),new NCalculateService(),new ZKCalculateService()}) {
            invalid(strategy,"100","-1"); invalid(strategy,"100","garbage"); invalid(strategy,"100",null);
            invalid(strategy,"100","1000000");
        }
        invalid(new NCalculateService(),"100","0"); invalid(new NCalculateService(),"100","100.001");
        invalid(new ZJCalculateService(),"100","101"); invalid(new ZKCalculateService(),"100","1.01"); invalid(new ZKCalculateService(),"100","0");
        for(String expression:new String[]{"100,-1","-1,1","100,101","0,0","100","100,1,2","100,",null}) invalid(new MJCalculateService(),"100",expression);
    }
    @Test public void originalAndSnapshotBoundaries() {
        for(String original:new String[]{"-1","0","0.001","1.001","1000000"}) invalid(new NCalculateService(),original,"0.01");
        assertEquals(new BigDecimal("999999.99"),calculate(new NCalculateService(),"999999.99","999999.99"));
        PricePolicy.validateSnapshot(new BigDecimal("100.00"),new BigDecimal("20.00"),new BigDecimal("80.00"));
        try { PricePolicy.validateSnapshot(new BigDecimal("100"),new BigDecimal("20"),new BigDecimal("79.99")); fail(); }
        catch(AppException expected) { assertEquals("E0111",expected.getCode()); }
    }
}
