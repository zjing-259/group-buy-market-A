package cn.bugstack.domain.activity.service.discount;

import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** DECIMAL(8,2), HALF_UP, no zero-price orders. */
public final class PricePolicy {
    private static final BigDecimal MAX = new BigDecimal("999999.99");
    private static final BigDecimal MIN = new BigDecimal("0.01");
    private PricePolicy() { }

    public static BigDecimal original(BigDecimal value) {
        if (value == null || value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) fail();
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new AppException(ResponseCode.INVALID_PRICE); }
    }

    public static BigDecimal payable(BigDecimal original, BigDecimal raw) {
        original(original);
        if (raw == null || raw.signum() < 0 || raw.compareTo(original) > 0) fail();
        return raw.setScale(2, RoundingMode.HALF_UP).max(MIN);
    }

    public static BigDecimal nonNegative(String expression) {
        try {
            BigDecimal value = new BigDecimal(expression);
            if (value.signum() < 0 || value.compareTo(MAX) > 0) fail();
            return value;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(ResponseCode.INVALID_PRICE);
        }
    }

    public static void validateSnapshot(BigDecimal original, BigDecimal deduction, BigDecimal payable) {
        original(original); original(payable);
        if (deduction == null || deduction.signum() < 0 || original.subtract(payable).compareTo(deduction) != 0) fail();
        try { deduction.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { fail(); }
    }

    public static void fail() { throw new AppException(ResponseCode.INVALID_PRICE); }
}
