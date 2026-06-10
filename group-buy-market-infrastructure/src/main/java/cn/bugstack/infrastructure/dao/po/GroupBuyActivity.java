package cn.bugstack.infrastructure.dao.po;

import lombok.Data;
import java.util.Date;

@Data
public class GroupBuyActivity {
    private Long id;                    // 自增ID
    private Long activityId;            // 活动ID
    private String activityName;        // 活动名称
    private String source;              // 来源
    private String channel;             // 渠道
    private String goodsId;             // 商品ID
    private String discountId;          // 折扣ID
    private Integer groupType;          // 拼团方式
    private Integer takeLimitCount;     // 拼团次数限制
    private Integer target;             // 拼团目标
    private Integer validTime;          // 拼团时长
    private Integer status;             // 活动状态
    private Date startTime;             // 活动开始时间
    private Date endTime;               // 活动结束时间
    private String tagId;               // 人群标签规则标识
    private String tagScope;            // 人群标签规则范围
    private Date createTime;            // 创建时间
    private Date updateTime;            // 更新时间
}
