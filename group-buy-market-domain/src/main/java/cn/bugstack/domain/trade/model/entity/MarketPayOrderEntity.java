package cn.bugstack.domain.trade.model.entity;

import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class MarketPayOrderEntity {
    private String outTradeNo;
    private String userId;
    private Long activityId;
    private String goodsId;
    private String source;
    private String channel;
    private String requestTeamId;
    private String notifyUrl;
    private BigDecimal originalPrice;
    private BigDecimal payPrice;
    private java.util.Date createTime;
    private java.util.Date outTradeTime;
    /** 拼单组队ID */
    private String teamId;
    /** 预购订单ID */
    private String orderId;
    /** 折扣金额 */
    private BigDecimal deductionPrice;
    /** 交易订单状态枚举 */
    private TradeOrderStatusEnumVO tradeOrderStatusEnumVO;

}
