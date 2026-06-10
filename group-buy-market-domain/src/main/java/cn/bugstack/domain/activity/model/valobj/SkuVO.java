package cn.bugstack.domain.activity.model.valobj;


import lombok.*;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Builder
public class SkuVO {
    /**
     * 商品ID
     */
    private String goodsId;

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品价格
     */
    private BigDecimal originalPrice;
}
