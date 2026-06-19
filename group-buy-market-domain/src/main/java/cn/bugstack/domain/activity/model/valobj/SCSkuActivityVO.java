package cn.bugstack.domain.activity.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SCSkuActivityVO {
    private Long activityId;            // 活动ID
    private String source;              // 来源
    private String channel;             // 渠道
    private String goodsId;             // 商品ID
}
