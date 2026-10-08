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
public class GroupBuyOrder {
    private String goodsId;
    /** Creation parameter; persisted as database-generated valid_start_time / valid_end_time. */
    private Integer validTime;
    /** Current activity bounds used by the conditional reservation write. */
    private Date activityStartTime;
    private Date activityEndTime;
    /** 自增ID */
    private Long id;
    /** 拼单组队ID */
    private String teamId;
    /** 活动ID */
    private Long activityId;
    /** 渠道 */
    private String source;
    /** 来源 */
    private String channel;
    /** 原始价格 */
    private BigDecimal originalPrice;
    /** 折扣金额 */
    private BigDecimal deductionPrice;
    /** 支付价格 */
    private BigDecimal payPrice;
    /** 目标成团人数 */
    private Integer targetCount;
    /** 实际完成人数 */
    private Integer completeCount;
    /** 已锁定（占位）人数 */
    private Integer lockCount;
    /** 状态（0-拼单中、1-完成、2-失败） */
    private Integer status;
    /** 拼团开始时间 - 参与拼团时间 */
    private Date validStartTime;
    /** 拼团结束时间 - 拼团有效时长 */
    private Date validEndTime;
    /** 回调通知地址 */
    private String notifyUrl;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;


}
