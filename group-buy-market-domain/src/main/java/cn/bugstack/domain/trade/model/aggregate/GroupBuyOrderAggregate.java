package cn.bugstack.domain.trade.model.aggregate;

import cn.bugstack.domain.trade.model.entity.PayActivityEntity;
import cn.bugstack.domain.trade.model.entity.PayDiscountEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class GroupBuyOrderAggregate {

    private UserEntity userEntity;

    private PayActivityEntity payActivityEntity;

    private PayDiscountEntity payDiscountEntity;

}
