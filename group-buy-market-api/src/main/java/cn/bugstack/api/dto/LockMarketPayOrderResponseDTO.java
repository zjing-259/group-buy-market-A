package cn.bugstack.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

//将锁单结果从服务层传递回 HTTP 调用方（前端或其他服务），保证数据格式统一。
@Data
@Builder
public class LockMarketPayOrderResponseDTO {

    /** 预购订单ID */
    private String orderId;
    /** 折扣金額 */
    private BigDecimal deductionPrice;
    /** 交易订单状态 */
    private Integer tradeOrderStatus;

}
