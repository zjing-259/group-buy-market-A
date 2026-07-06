package cn.bugstack.domain.trade.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum TradeOrderStatusEnumVO {
    CREATE(0,"初始创建"),
    COMPLETE(1, "消费完成"),
    CLOSE(2,"超市关单")
    ;

    private Integer code;
    private String info;

    //将传入参数转换成对应枚举对象
    public static TradeOrderStatusEnumVO valueOf(Integer code){
        switch(code){
            case 0:
                return CREATE;
            case 1:
                return COMPLETE;
            case 2:
                return CLOSE;
        }

        return CREATE;
    }
}
