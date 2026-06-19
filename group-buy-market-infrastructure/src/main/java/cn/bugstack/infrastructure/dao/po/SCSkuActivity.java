package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SCSkuActivity {
    private Long id;
    private Long activityId;            // 活动ID
    private String source;              // 来源
    private String channel;             // 渠道
    private String goodsId;             // 商品ID
    private Date createTime;            // 创建时间
    private Date updateTime;            // 更新时间

}
