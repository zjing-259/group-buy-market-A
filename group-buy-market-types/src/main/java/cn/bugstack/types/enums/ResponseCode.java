package cn.bugstack.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    INDEX_EXCEPTION("0003", "唯一索引冲突"),
    UPDATE_ZERO("0004", "更新记录为0"),
    HTTP_EXCEPTION("0005", "HTTP接口调用异常"),

    E0001("E0001","不存在对应计算服务"),
    E0002("E0002","无拼团营销配置"),
    E0003("E0003","拼团活动降级拦截"),
    E0004("E0004","拼团活动切量拦截"),
    E0005("E0005","拼团组队失败，更新记录为0"),
    E0006("E0006","拼团组队完结，锁单量已达成"),
    E0007("E0007","拼团人数限定，不可参与"),

    TRADE_CONFLICT("E0107", "外部单号请求参数冲突或历史请求身份无法核验"),
    ACTIVITY_MISMATCH("E0108", "商品映射活动与请求活动不一致"),
    TEAM_INVALID("E0109", "团队不存在、不可参与或归属不一致"),
    NOT_ELIGIBLE("E0110", "用户不具备活动参与资格"),
    INVALID_PRICE("E0111", "价格或优惠配置非法"),
    CHANNEL_MISMATCH("E0112", "订单用户或来源渠道不一致"),
    PAYMENT_CONFLICT("E0113", "支付事实与已结算订单不一致"),
    E0101("E0101", "拼团活动未生效"),
    E0102("E0102", "拼团活动已失效"),
    E0103("E0103", "当前用户参与此拼团次数已达上限"),
    E0104("E0104", "不存在的外部交易单号或用户已退单"),
    E0105("E0105", "SC渠道黑名单拦截"),
    E0106("E0106", "订单交易时间不在拼团有效时间范围内"),

    ;

    private String code;
    private String info;

}
