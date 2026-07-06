package cn.bugstack.domain.trade.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class GroupBuyProgressVO {
    //目标量
    private Integer targetCount;
    //完成量
    private Integer completeCount;
    //锁单量
    private Integer lockCount;
}
