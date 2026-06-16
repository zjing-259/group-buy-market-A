package cn.bugstack.domain.activity.model.valobj;


import lombok.*;

import java.util.Date;

@Data
@AllArgsConstructor
@Builder
public class GroupBuyActivityDiscountVO {
    private Long id;                    // 自增ID
    private Long activityId;            // 活动ID
    private String activityName;        // 活动名称
    private String source;              // 来源
    private String channel;             // 渠道
    private String goodsId;             // 商品ID
    private GroupBuyDiscount groupBuyDiscount;          // 折扣
    private Integer groupType;          // 拼团方式
    private Integer takeLimitCount;     // 拼团次数限制
    private Integer target;             // 拼团目标
    private Integer validTime;          // 拼团时长
    private Integer status;             // 活动状态
    private Date startTime;             // 活动开始时间
    private Date endTime;               // 活动结束时间
    private String tagId;               // 人群标签规则标识
    private String tagScope;            // 人群标签规则范围

    @Data
    @AllArgsConstructor
    @Builder
    public static class GroupBuyDiscount{
        private Long id;                    // 自增ID
        private Integer discountId;         // 折扣ID
        private String discountName;        // 折扣标题
        private String discountDesc;        // 折扣描述
        private DiscountTypeEnum discountType;       // 折扣类型
        private String marketPlan;          // 营销优惠计划
        private String marketExpr;          // 营销优惠表达式
        private String tagId;               // 人群标签
    }
}
