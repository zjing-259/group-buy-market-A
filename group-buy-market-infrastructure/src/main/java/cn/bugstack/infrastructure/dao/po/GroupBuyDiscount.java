package cn.bugstack.infrastructure.dao.po;

import cn.bugstack.domain.activity.model.valobj.DiscountTypeEnum;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class GroupBuyDiscount {
    private Long id;                    // 自增ID
    private Integer discountId;         // 折扣ID
    private String discountName;        // 折扣标题
    private String discountDesc;        // 折扣描述
    private DiscountTypeEnum discountType;       // 折扣类型
    private String marketPlan;          // 营销优惠计划
    private String marketExpr;          // 营销优惠表达式
    private String tagId;               // 人群标签
    private Date createTime;            // 创建时间
    private Date updateTime;            // 更新时间
}