package cn.bugstack.domain.activity.model.valobj;


import cn.bugstack.types.common.Constants;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.Objects;

/**
 * GroupBuyActivityDiscountVO是拼团「活动 + 折扣」配置的聚合值对象（Value Object）
 * 这不是 PO（持久化对象），而是 domain 层的 VO，起防腐/隔离作用——领域内部用 VO 传聚合信息，不直接把数据库 PO 抛出去。
 * 2026/9/1 16:55
 * @author 正经
 */
@Getter
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

    /**
     * 可见限制
     * 只要存在这样一个值，那么首次获得的默认值就是 false
     */
    public boolean isVisible(){
        if (StringUtils.isBlank(this.tagScope)) {
            return TagScopeEnumVO.VISIBLE.isAllow();
        }

        String[] split = this.tagScope.split(Constants.SPLIT);
        if((split.length > 0 && Objects.equals(split[0],"1")) && StringUtils.isNotBlank(split[0])){
            return TagScopeEnumVO.VISIBLE.isRefuse();
        }
        return TagScopeEnumVO.VISIBLE.isAllow();
    }

    /**
     * 参与限制
     * 只要存在这样一个值，那么首次获得的默认值就是 false
     */
    public boolean isEnable(){
        if (StringUtils.isBlank(this.tagScope)) {
            return TagScopeEnumVO.ENABLE.isAllow();
        }

        String[] split = this.tagScope.split(Constants.SPLIT);
        if((split.length == 2 && Objects.equals(split[1],"2")) && StringUtils.isNotBlank(split[1])){
            return TagScopeEnumVO.ENABLE.isRefuse();
        }
        return TagScopeEnumVO.ENABLE.isAllow();
    }

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
