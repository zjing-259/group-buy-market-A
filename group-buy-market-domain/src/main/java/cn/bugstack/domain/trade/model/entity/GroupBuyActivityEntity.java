package cn.bugstack.domain.trade.model.entity;

import cn.bugstack.types.enums.ActivityStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class GroupBuyActivityEntity {

    private Long activityId;            // 活动ID
    private String activityName;        // 活动名称
    private String discountId;          // 折扣ID
    private Integer groupType;          // 拼团方式
    private Integer takeLimitCount;     // 拼团次数限制
    private Integer target;             // 拼团目标
    private Integer validTime;          // 拼团时长
    private ActivityStatusEnumVO status;             // 活动状态(0:创建 1:生效 2:过期 3:废弃)
    private Date startTime;             // 活动开始时间
    private Date endTime;               // 活动结束时间
    private String tagId;               // 人群标签规则标识
    private String tagScope;            // 人群标签规则范围
}
