package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class V2PaymentEngine {
    @Resource private JdbcTemplate jdbc;
    @Resource private V2TradeEngine trade;
    @Resource private V2OutboxService outbox;
    @Resource private V2TeamEngine teams;
    @Resource private V2PaymentIdentity identity;

    public static final class Applied {
        public String outcome;
        public Object result;
        public Applied(String outcome, Object result) { this.outcome=outcome; this.result=result; }
    }
    private Map<String,Object> first(String sql, Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args); return rows.isEmpty()?null:rows.get(0);
    }
    private static String string(Map<String,Object> row, String key) { return row.get(key)==null?null:row.get(key).toString(); }
    private static Timestamp stamp(Map<String,Object> row, String key) { return (Timestamp)row.get(key); }
    private static boolean same(String a,String b) { return Objects.equals(a,b); }

    public Applied apply(EventEnvelope envelope, PaymentSucceeded fact) {
        V2Format.id(fact.getUserId(),64); V2Format.id(fact.getOutTradeNo(),64);
        V2Format.id(fact.getPaymentId(),64); V2Format.currency(fact.getCurrency());
        BigDecimal paidAmount=V2Format.money(fact.getPaidAmount());
        Instant paidAt=V2Format.instant(fact.getPaidAt());
        jdbc.update("insert into ab_trade_guard(source,channel,out_trade_no) values(?,?,?) " +
                        "on duplicate key update out_trade_no=values(out_trade_no)",
                envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        Map<String,Object> order=first("select * from ab_trade_order where source=? and channel=? and out_trade_no=? for update",
                envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        if (order==null) throw new V2Wait("ORDER_NOT_YET_RESERVED");
        if (!same(fact.getUserId(),string(order,"user_id"))) throw new V2Failure("E0112");
        if ("PAID".equals(string(order,"order_status"))) {
            if (!same(fact.getPaymentId(),string(order,"payment_id")) ||
                    paidAmount.compareTo((BigDecimal)order.get("paid_amount"))!=0 ||
                    !stamp(order,"paid_at").toInstant().equals(paidAt)) throw new V2Failure("E0113");
            return new Applied("IGNORED",paymentResult(order));
        }
        if ("CANCELLED".equals(string(order,"order_status"))) throw new V2Failure(V2Codes.ORDER_CLOSED);
        if (!"RESERVATION".equals(string(order,"record_kind"))) throw new V2Failure(V2Codes.ORDER_CLOSED);
        if (paidAmount.compareTo((BigDecimal)order.get("pay_price"))!=0)
            throw new V2Failure(V2Codes.PAYMENT_AMOUNT_MISMATCH);
        if (paidAt.isBefore(stamp(order,"reserved_at").toInstant()) ||
                !paidAt.isBefore(stamp(order,"payment_deadline").toInstant()))
            throw new V2Failure("PAYMENT_OUTSIDE_WINDOW");
        Map<String,Object> team=first("select * from ab_team where team_id=? for update",string(order,"team_id"));
        // The team lock can be held past its deadline; sample the database clock only after it is acquired.
        Timestamp databaseNow=jdbc.queryForObject("select now(3)",Timestamp.class);
        if (paidAt.isAfter(databaseNow.toInstant())) throw new V2Failure("PAYMENT_OUTSIDE_WINDOW");
        if (team==null || "FAILED".equals(string(team,"team_status"))) throw new V2Failure("E0109");
        if (!"FORMING".equals(string(team,"team_status")) && !"RECONCILING".equals(string(team,"team_status")))
            throw new V2Failure("E0109");
        identity.bind(envelope.getSource(),envelope.getChannel(),fact.getPaymentId(),fact.getOutTradeNo());
        if ("FORMING".equals(string(team,"team_status")) && !databaseNow.before(stamp(team,"valid_end_time"))) {
            teams.beginReconciliationLocked(team);
            team=first("select * from ab_team where team_id=?",string(order,"team_id"));
        }
        if (!"FORMING".equals(string(team,"team_status")) && !"RECONCILING".equals(string(team,"team_status")))
            throw new V2Failure("E0109");
        jdbc.update("update ab_trade_order set order_status='PAID',payment_id=?,paid_amount=?,paid_at=?," +
                "record_version=record_version+1 where source=? and channel=? and out_trade_no=? and order_status='RESERVED'",
                fact.getPaymentId(),paidAmount,Timestamp.from(paidAt),envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        int count=((Number)team.get("paid_count")).intValue()+1;
        int target=((Number)team.get("target_count")).intValue();
        boolean completed=count>=target;
        jdbc.update("update ab_team set paid_count=paid_count+1,team_version=team_version+1,team_status=?," +
                "resolved_at=case when ?='COMPLETED' then now(3) else resolved_at end where team_id=?",
                completed?"COMPLETED":string(team,"team_status"),completed?"COMPLETED":string(team,"team_status"),string(team,"team_id"));
        if (completed) {
            List<String> orderNumbers=jdbc.query("select out_trade_no from ab_trade_order where team_id=? and order_status='PAID' order by out_trade_no",
                    (rs,n)->rs.getString(1),string(team,"team_id"));
            TeamCompleted payload=new TeamCompleted(); payload.setTeamId(string(team,"team_id"));
            payload.setActivityId(string(team,"activity_id"));
            payload.setTeamVersion(Long.toString(((Number)team.get("team_version")).longValue()+1));
            payload.setOutTradeNoList(orderNumbers);
            outbox.enqueue("A_TEAM_EVENTS","TEAM_COMPLETED",envelope.getSource(),envelope.getChannel(),envelope.getEventId(),payload);
        }
        Map<String,Object> updated=first("select * from ab_trade_order where source=? and channel=? and out_trade_no=?",
                envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        return new Applied("APPLIED",paymentResult(updated));
    }

    private Object paymentResult(Map<String,Object> order) {
        Reservation r=trade.toReservation(order);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("orderId",r.getOrderId()); result.put("outTradeNo",r.getOutTradeNo());
        result.put("teamId",r.getTeamId()); result.put("payment",r.getPayment());
        result.put("orderStatus",r.getOrderStatus()); result.put("teamStatus",r.getTeamStatus());
        result.put("recordVersion",r.getRecordVersion()); result.put("teamVersion",r.getTeamVersion());
        return result;
    }
}
