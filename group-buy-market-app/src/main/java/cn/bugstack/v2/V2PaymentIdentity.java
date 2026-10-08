package cn.bugstack.v2;

import cn.bugstack.api.v2.V2Codes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

/** Shared binding for accepted and exceptional payments; caller holds the order lock. */
@Service
public class V2PaymentIdentity {
    @Resource private JdbcTemplate jdbc;

    public void bind(String source, String channel, String paymentId, String outTradeNo) {
        jdbc.update("insert into ab_payment_guard(source,channel,payment_id,out_trade_no) values(?,?,?,?) " +
                "on duplicate key update payment_id=values(payment_id)",source,channel,paymentId,outTradeNo);
        String bound=jdbc.queryForObject("select out_trade_no from ab_payment_guard where source=? and channel=? " +
                "and payment_id=?",String.class,source,channel,paymentId);
        if (!outTradeNo.equals(bound)) throw new V2Failure(V2Codes.PAYMENT_ID_CONFLICT);
    }
}
