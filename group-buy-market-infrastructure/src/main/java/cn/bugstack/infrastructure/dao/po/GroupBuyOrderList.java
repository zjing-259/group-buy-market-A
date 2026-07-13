package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class GroupBuyOrderList {
    private Long id;
    private String userId;
    private String teamId;
    private String orderId;
    private Long activityId;
    private Date startTime;
    private Date endTime;
    private String goodsId;
    private String source;
    private String channel;
    private BigDecimal originalPrice;
    private BigDecimal deductionPrice;
    private Integer status;
    private String outTradeNo;
    private String bizId;
    private Date createTime;
    private Date updateTime;
}
