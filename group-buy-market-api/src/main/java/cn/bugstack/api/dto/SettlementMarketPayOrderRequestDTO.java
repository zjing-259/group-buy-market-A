package cn.bugstack.api.dto;

import lombok.Data;

import java.util.Date;

/**
 * 结算请求对象
 * 2026/9/4 16:21
 * @author 正经
 */
@Data
public class SettlementMarketPayOrderRequestDTO {
    /** 渠道 */
    private String source;
    /** 来源 */
    private String channel;
    /** 用户ID */
    private String userId;
    /** 外部交易单号 */
    private String outTradeNo;
    /** 外部交易时间 */
    private Date outTradeTime;

}
