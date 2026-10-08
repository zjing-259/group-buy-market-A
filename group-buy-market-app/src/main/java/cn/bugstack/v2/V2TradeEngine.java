package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class V2TradeEngine {
    @Resource private JdbcTemplate jdbc;
    @Resource private V2MarketingEngine marketing;

    private List<Map<String,Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    private Map<String,Object> first(String sql, Object... args) {
        List<Map<String,Object>> found = rows(sql, args); return found.isEmpty() ? null : found.get(0);
    }
    private Timestamp now() { return jdbc.queryForObject("select now(3)", Timestamp.class); }
    private static String string(Map<String,Object> row, String key) { Object v=row.get(key); return v == null ? null : v.toString(); }
    private static int number(Map<String,Object> row, String key) { return ((Number)row.get(key)).intValue(); }
    private static String version(Map<String,Object> row, String key) { return row.get(key) == null ? null : row.get(key).toString(); }
    private static Timestamp stamp(Map<String,Object> row, String key) { return (Timestamp)row.get(key); }
    private static boolean exact(String a, String b) { return Objects.equals(a,b); }

    private void guard(String source, String channel, String outTradeNo) {
        jdbc.update("insert into ab_trade_guard(source,channel,out_trade_no) values(?,?,?) " +
                "on duplicate key update out_trade_no=values(out_trade_no)",source,channel,outTradeNo);
    }
    private Map<String,Object> order(String source, String channel, String outTradeNo, boolean lock) {
        return first("select * from ab_trade_order where source=? and channel=? and out_trade_no=?" +
                        (lock ? " for update" : ""), source,channel,outTradeNo);
    }
    private Map<String,Object> legacyOrder(String source,String channel,String outTradeNo) {
        return first("select user_id from group_buy_order_list where source=? and channel=? and out_trade_no=? " +
                        "and binary source=binary ? and binary channel=binary ? and binary out_trade_no=binary ?",
                source,channel,outTradeNo,source,channel,outTradeNo);
    }
    private void rejectLegacy(String source,String channel,String outTradeNo,String userId) {
        Map<String,Object> old=legacyOrder(source,channel,outTradeNo);
        if (old==null) return;
        if (!exact(userId,string(old,"user_id"))) throw new V2Failure(V2Codes.FORBIDDEN);
        throw new V2Failure(V2Codes.LEGACY_FACT_UNVERIFIABLE);
    }
    private Map<String,Object> team(String teamId, boolean lock) {
        return first("select * from ab_team where team_id=?" + (lock ? " for update" : ""), teamId);
    }
    private void requestBase(BaseRequest request) {
        if (request == null) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        V2Format.id(request.getSource(),8); V2Format.id(request.getChannel(),8);
    }

    public Reservation toReservation(Map<String,Object> row) {
        Reservation r = new Reservation();
        r.setRecordKind(string(row,"record_kind")); r.setOrderId(string(row,"order_id"));
        r.setOutTradeNo(string(row,"out_trade_no")); r.setUserId(string(row,"user_id"));
        r.setSource(string(row,"source")); r.setChannel(string(row,"channel"));
        r.setActivityId(version(row,"activity_id")); r.setGoodsId(string(row,"goods_id"));
        r.setProductVersion(version(row,"product_version"));
        r.setQuantity(row.get("quantity") == null ? null : number(row,"quantity"));
        r.setTeamId(string(row,"team_id"));
        if (row.get("original_price") != null) r.setOriginalPrice(V2Format.money((BigDecimal)row.get("original_price")));
        if (row.get("deduction_price") != null) r.setDeductionPrice(V2Format.money((BigDecimal)row.get("deduction_price")));
        if (row.get("pay_price") != null) r.setPayPrice(V2Format.money((BigDecimal)row.get("pay_price")));
        r.setCurrency(string(row,"currency")); r.setReservedAt(V2Format.utc(stamp(row,"reserved_at")));
        r.setPaymentDeadline(V2Format.utc(stamp(row,"payment_deadline")));
        r.setOrderStatus(string(row,"order_status")); r.setRecordVersion(version(row,"record_version"));
        r.setClosedAt(V2Format.utc(stamp(row,"closed_at")));
        if (row.get("payment_id") != null) {
            Payment p = new Payment(); p.setPaymentId(string(row,"payment_id"));
            p.setPaidAmount(V2Format.money((BigDecimal)row.get("paid_amount")));
            p.setCurrency("CNY"); p.setPaidAt(V2Format.utc(stamp(row,"paid_at"))); r.setPayment(p);
        }
        if (row.get("refund_id") != null) {
            Refund refund = new Refund(); refund.setRefundId(string(row,"refund_id"));
            refund.setRefundStatus(string(row,"refund_status")); refund.setRefundVersion(version(row,"refund_version"));
            refund.setRefundAmount(V2Format.money((BigDecimal)row.get("refund_amount")));
            refund.setCurrency("CNY"); r.setRefund(refund);
        }
        if (r.getTeamId() != null) {
            Map<String,Object> team = team(r.getTeamId(), false);
            if (team != null) { r.setTeamStatus(string(team,"team_status")); r.setTeamVersion(version(team,"team_version")); }
        }
        r.setServerTime(V2Format.now());
        return r;
    }

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public Reservation reserve(ReserveRequest request) {
        requestBase(request);
        V2Format.id(request.getUserId(),64); V2Format.id(request.getOutTradeNo(),64);
        V2Format.id(request.getGoodsId(),64); V2Format.activityId(request.getActivityId());
        long productVersion = V2Format.positiveVersion(request.getProductVersion());
        V2Format.currency(request.getCurrency());
        BigDecimal expected = V2Format.money(request.getExpectedPayPrice());
        if (!Integer.valueOf(1).equals(request.getQuantity())) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        V2Format.optionalId(request.getTeamId(),64);
        guard(request.getSource(),request.getChannel(),request.getOutTradeNo());
        Map<String,Object> old = order(request.getSource(),request.getChannel(),request.getOutTradeNo(),true);
        if (old != null) {
            if ("CLOSE_MARKER".equals(string(old,"record_kind")) || "CANCELLED".equals(string(old,"order_status")))
                throw new V2Failure(V2Codes.ORDER_CLOSED);
            if (!exact(request.getUserId(),string(old,"user_id")) ||
                    !exact(request.getGoodsId(),string(old,"goods_id")) ||
                    !exact(request.getActivityId(),version(old,"activity_id")) ||
                    !exact(request.getProductVersion(),version(old,"product_version")) ||
                    !exact(request.getTeamId(),string(old,"request_team_id")) ||
                    number(old,"quantity") != 1 ||
                    expected.compareTo((BigDecimal)old.get("pay_price")) != 0)
                throw new V2Failure("E0107");
            return toReservation(old);
        }

        rejectLegacy(request.getSource(),request.getChannel(),request.getOutTradeNo(),request.getUserId());

        // Serialize all new orders for a user/activity, including different SKUs and channels.
        jdbc.update("insert into trade_quota_guard(user_id,activity_id) values(?,?) " +
                        "on duplicate key update activity_id=values(activity_id)",
                request.getUserId(),Long.parseLong(request.getActivityId()));

        TrialRequest trial = new TrialRequest(); trial.setSource(request.getSource()); trial.setChannel(request.getChannel());
        trial.setUserId(request.getUserId()); trial.setGoodsId(request.getGoodsId());
        trial.setQuantity(1); trial.setProductVersion(request.getProductVersion()); trial.setActivityId(request.getActivityId());
        V2MarketingEngine.Quote quote = marketing.quote(trial,true);
        if (quote == null || !quote.eligible) throw new V2Failure("E0110");
        if (expected.compareTo(V2Format.money(quote.offer.getPayPrice())) != 0) throw new V2Failure(V2Codes.PRICE_CHANGED);
        GroupBuyActivityDiscountVO activity = quote.activity;
        Timestamp reservedAt;
        Integer takeLimit = activity.getTakeLimitCount();
        Integer used = jdbc.queryForObject("select (select count(*) from ab_trade_order where user_id=? and activity_id=? " +
                "and binary user_id=binary ?) + (select count(*) from group_buy_order_list where user_id=? and activity_id=? " +
                "and binary user_id=binary ?)",Integer.class,request.getUserId(),activity.getActivityId(),request.getUserId(),
                request.getUserId(),activity.getActivityId(),request.getUserId());
        if (takeLimit != null && used != null && used >= takeLimit) throw new V2Failure("E0103");
        String teamId = request.getTeamId();
        Timestamp deadline;
        if (teamId == null) {
            reservedAt = now();
            if (reservedAt.before(activity.getStartTime()) || !reservedAt.before(activity.getEndTime()))
                throw new V2Failure("E0102");
            teamId = UUID.randomUUID().toString();
            deadline = Timestamp.from(reservedAt.toInstant().plusSeconds(activity.getValidTime()*60L));
            jdbc.update("insert into ab_team(team_id,source,channel,activity_id,goods_id,target_count,reserved_count,paid_count," +
                            "team_status,team_version,valid_start_time,valid_end_time) values(?,?,?,?,?, ?,1,0,'FORMING',1,?,?)",
                    teamId,request.getSource(),request.getChannel(),activity.getActivityId(),request.getGoodsId(),
                    activity.getTarget(),reservedAt,deadline);
        } else {
            Map<String,Object> team = team(teamId,true);
            // Read the database clock after obtaining the team lock: a wait can cross the deadline.
            reservedAt = now();
            if (reservedAt.before(activity.getStartTime()) || !reservedAt.before(activity.getEndTime()))
                throw new V2Failure("E0102");
            if (team == null || !exact(request.getSource(),string(team,"source")) ||
                    !exact(request.getChannel(),string(team,"channel")) ||
                    !exact(request.getGoodsId(),string(team,"goods_id")) ||
                    !exact(request.getActivityId(),version(team,"activity_id")) ||
                    !"FORMING".equals(string(team,"team_status")) ||
                    number(team,"reserved_count") >= number(team,"target_count") ||
                    reservedAt.before(stamp(team,"valid_start_time")) || !reservedAt.before(stamp(team,"valid_end_time")))
                throw new V2Failure("E0109");
            deadline = stamp(team,"valid_end_time");
            jdbc.update("update ab_team set reserved_count=reserved_count+1,team_version=team_version+1 where team_id=?",teamId);
        }
        String orderId = UUID.randomUUID().toString();
        jdbc.update("insert into ab_trade_order(source,channel,out_trade_no,record_kind,order_id,user_id,goods_id," +
                        "activity_id,product_version,quantity,request_team_id,team_id,original_price,deduction_price,pay_price," +
                        "currency,reserved_at,payment_deadline,order_status,record_version) " +
                        "values(?,?,?,'RESERVATION',?,?,?,?,?,?,?,?,?,?,?,?,?,?,'RESERVED',1)",
                request.getSource(),request.getChannel(),request.getOutTradeNo(),orderId,request.getUserId(),
                request.getGoodsId(),activity.getActivityId(),productVersion,1,request.getTeamId(),teamId,
                V2Format.money(quote.offer.getOriginalPrice()),V2Format.money(quote.offer.getDeductionPrice()),expected,
                "CNY",reservedAt,deadline);
        return toReservation(order(request.getSource(),request.getChannel(),request.getOutTradeNo(),false));
    }

    public Reservation query(TradeQueryRequest request) {
        requestBase(request); V2Format.id(request.getUserId(),64); V2Format.id(request.getOutTradeNo(),64);
        Map<String,Object> found = order(request.getSource(),request.getChannel(),request.getOutTradeNo(),false);
        if (found == null) {
            rejectLegacy(request.getSource(),request.getChannel(),request.getOutTradeNo(),request.getUserId());
            throw new V2Failure(V2Codes.NOT_FOUND);
        }
        if (!exact(request.getUserId(),string(found,"user_id"))) throw new V2Failure(V2Codes.FORBIDDEN);
        return toReservation(found);
    }

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public CloseResponse close(CloseRequest request) {
        requestBase(request); V2Format.id(request.getUserId(),64); V2Format.id(request.getOutTradeNo(),64);
        V2Format.id(request.getCloseId(),64); V2Format.instant(request.getVerifiedAt());
        if (!Arrays.asList("USER_CANCEL","PAYMENT_TIMEOUT","STOCK_FAILURE","ORDER_ABORT").contains(request.getReason()) ||
                !Arrays.asList("NEVER_OPENED","CLOSED_UNPAID").contains(request.getPaymentDisposition()))
            throw new V2Failure(V2Codes.INVALID_PARAMETER);
        String closeHash = V2Format.sha256(request.getCloseId()+"\n"+request.getReason()+"\n"+
                request.getPaymentDisposition()+"\n"+request.getVerifiedAt());
        guard(request.getSource(),request.getChannel(),request.getOutTradeNo());
        Map<String,Object> found = order(request.getSource(),request.getChannel(),request.getOutTradeNo(),true);
        if (found == null) {
            rejectLegacy(request.getSource(),request.getChannel(),request.getOutTradeNo(),request.getUserId());
            Timestamp closed = now();
            jdbc.update("insert into ab_trade_order(source,channel,out_trade_no,record_kind,user_id,order_status," +
                            "record_version,close_id,close_hash,closed_at) values(?,?,?,'CLOSE_MARKER',?,'CANCELLED',1,?,?,?)",
                    request.getSource(),request.getChannel(),request.getOutTradeNo(),request.getUserId(),
                    request.getCloseId(),closeHash,closed);
            found = order(request.getSource(),request.getChannel(),request.getOutTradeNo(),false);
        } else {
            if (!exact(request.getUserId(),string(found,"user_id"))) throw new V2Failure(V2Codes.FORBIDDEN);
            if ("PAID".equals(string(found,"order_status"))) throw new V2Failure(V2Codes.ORDER_ALREADY_PAID);
            if ("CANCELLED".equals(string(found,"order_status"))) {
                if (exact(request.getCloseId(),string(found,"close_id")) &&
                        !exact(closeHash,string(found,"close_hash"))) throw new V2Failure(V2Codes.CLOSE_CONFLICT);
            } else {
                String teamId = string(found,"team_id");
                if (teamId != null) {
                    Map<String,Object> team = team(teamId,true);
                    if (team == null || "COMPLETED".equals(string(team,"team_status"))) throw new V2Failure(V2Codes.CLOSE_CONFLICT);
                    jdbc.update("update ab_team set reserved_count=reserved_count-1,team_version=team_version+1 " +
                            "where team_id=? and reserved_count>0",teamId);
                }
                jdbc.update("update ab_trade_order set order_status='CANCELLED',record_version=record_version+1," +
                                "close_id=?,close_hash=?,closed_at=? where source=? and channel=? and out_trade_no=?",
                        request.getCloseId(),closeHash,now(),request.getSource(),request.getChannel(),request.getOutTradeNo());
                found = order(request.getSource(),request.getChannel(),request.getOutTradeNo(),false);
            }
        }
        CloseResponse result = new CloseResponse(); result.setOutTradeNo(request.getOutTradeNo());
        result.setOrderId(string(found,"order_id")); result.setTeamId(string(found,"team_id"));
        result.setOrderStatus("CANCELLED"); result.setClosedAt(V2Format.utc(stamp(found,"closed_at")));
        result.setRecordVersion(version(found,"record_version"));
        if (result.getTeamId()!=null) result.setTeamVersion(version(team(result.getTeamId(),false),"team_version"));
        return result;
    }
}
