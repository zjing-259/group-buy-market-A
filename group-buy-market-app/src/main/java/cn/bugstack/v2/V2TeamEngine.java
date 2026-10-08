package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class V2TeamEngine {
    @Resource private JdbcTemplate jdbc;
    @Resource private ObjectMapper json;
    @Resource private V2OutboxService outbox;
    @Resource private V2TradeEngine trades;
    @Resource private V2PaymentIdentity identity;

    private Map<String,Object> first(String sql,Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args); return rows.isEmpty()?null:rows.get(0);
    }
    private static String str(Map<String,Object> row,String key) { return row.get(key)==null?null:row.get(key).toString(); }
    private static Timestamp time(Map<String,Object> row,String key) { return (Timestamp)row.get(key); }
    private static long num(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static boolean same(String a,String b) { return Objects.equals(a,b); }
    private void checkScope(BaseRequest request) {
        if (request==null) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        V2Format.id(request.getSource(),8); V2Format.id(request.getChannel(),8);
    }
    private Map<String,Object> team(String source,String channel,String teamId,boolean lock) {
        Map<String,Object> row=first("select * from ab_team where team_id=?"+(lock?" for update":""),teamId);
        if (row==null) throw new V2Failure(V2Codes.NOT_FOUND);
        if (!same(source,str(row,"source")) || !same(channel,str(row,"channel"))) throw new V2Failure(V2Codes.FORBIDDEN);
        return row;
    }

    public Team query(TeamQueryRequest request) {
        checkScope(request); V2Format.id(request.getTeamId(),64); V2Format.optionalId(request.getUserId(),64);
        Map<String,Object> row=team(request.getSource(),request.getChannel(),request.getTeamId(),false);
        Team result=new Team(); result.setTeamId(str(row,"team_id")); result.setActivityId(str(row,"activity_id"));
        result.setGoodsId(str(row,"goods_id")); result.setSource(str(row,"source")); result.setChannel(str(row,"channel"));
        result.setTargetCount((int)num(row,"target_count")); result.setReservedCount((int)num(row,"reserved_count"));
        result.setPaidCount((int)num(row,"paid_count")); result.setTeamStatus(str(row,"team_status"));
        result.setTeamVersion(str(row,"team_version")); result.setValidStartTime(V2Format.utc(time(row,"valid_start_time")));
        result.setValidEndTime(V2Format.utc(time(row,"valid_end_time")));
        Timestamp now=jdbc.queryForObject("select now(3)",Timestamp.class);
        result.setJoinable("FORMING".equals(result.getTeamStatus()) && result.getReservedCount()<result.getTargetCount() &&
                !now.before(time(row,"valid_start_time")) && now.before(time(row,"valid_end_time")));
        result.setServerTime(V2Format.utc(now));
        if (request.getUserId()!=null) {
            Map<String,Object> own=first("select * from ab_trade_order where team_id=? and user_id=? " +
                    "and binary user_id=binary ? order by reserved_at desc limit 1",request.getTeamId(),request.getUserId(),request.getUserId());
            if (own!=null) result.setOwnReservation(trades.toReservation(own));
        }
        return result;
    }

    public void beginReconciliationLocked(Map<String,Object> current) {
        if (!"FORMING".equals(str(current,"team_status"))) return;
        List<String> roster=jdbc.query("select out_trade_no from ab_trade_order where team_id=? and record_kind='RESERVATION' " +
                "and order_status<>'CANCELLED' order by out_trade_no",(rs,n)->rs.getString(1),str(current,"team_id"));
        if (roster.size()>100) throw new V2Failure("RECONCILIATION_INCOMPLETE");
        String rosterJson;
        try { rosterJson=json.writeValueAsString(roster); }
        catch (Exception e) { throw new IllegalStateException(e); }
        String reconciliationId=UUID.randomUUID().toString();
        String rosterVersion=V2Format.sha256(rosterJson);
        jdbc.update("update ab_team set team_status='RECONCILING',team_version=team_version+1," +
                        "reconciliation_id=?,roster_version=?,roster_json=? where team_id=? and team_status='FORMING'",
                reconciliationId,rosterVersion,rosterJson,str(current,"team_id"));
        TeamReconciliationRequired payload=new TeamReconciliationRequired();
        payload.setTeamId(str(current,"team_id")); payload.setActivityId(str(current,"activity_id"));
        payload.setTeamVersion(Long.toString(num(current,"team_version")+1));
        payload.setOutTradeNoList(roster); payload.setReconciliationId(reconciliationId);
        payload.setRosterVersion(rosterVersion); payload.setValidEndTime(V2Format.utc(time(current,"valid_end_time")));
        outbox.enqueue("A_TEAM_EVENTS","TEAM_RECONCILIATION_REQUIRED",str(current,"source"),str(current,"channel"),null,payload);
    }

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void expire(String teamId) {
        Map<String,Object> row=first("select * from ab_team where team_id=? for update",teamId);
        if (row==null || !"FORMING".equals(str(row,"team_status"))) return;
        Timestamp now=jdbc.queryForObject("select now(3)",Timestamp.class);
        if (!now.before(time(row,"valid_end_time"))) beginReconciliationLocked(row);
    }

    @SuppressWarnings("unchecked")
    private List<String> roster(Map<String,Object> row) {
        try { return json.readValue(str(row,"roster_json"),new TypeReference<List<String>>(){}); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public ReconciliationSnapshot queryReconciliation(ReconciliationQueryRequest request) {
        checkScope(request); V2Format.id(request.getTeamId(),64);
        Map<String,Object> team=team(request.getSource(),request.getChannel(),request.getTeamId(),false);
        if (team.get("roster_json")==null) throw new V2Failure(V2Codes.RECONCILIATION_NOT_STARTED);
        ReconciliationSnapshot snapshot=new ReconciliationSnapshot();
        snapshot.setTeamStatus(str(team,"team_status")); snapshot.setTeamVersion(str(team,"team_version"));
        snapshot.setReconciliationId(str(team,"reconciliation_id")); snapshot.setRosterVersion(str(team,"roster_version"));
        snapshot.setValidEndTime(V2Format.utc(time(team,"valid_end_time")));
        List<ReconciliationMember> members=new ArrayList<>();
        for (String number:roster(team)) {
            Map<String,Object> row=first("select * from ab_trade_order where source=? and channel=? and out_trade_no=?",
                    request.getSource(),request.getChannel(),number);
            if (row==null) throw new V2Failure(V2Codes.RECONCILIATION_INCOMPLETE);
            Reservation reservation=trades.toReservation(row);
            ReconciliationMember member=new ReconciliationMember();
            member.setUserId(reservation.getUserId()); member.setOutTradeNo(number); member.setOrderId(reservation.getOrderId());
            member.setOrderStatus(reservation.getOrderStatus()); member.setReservedAt(reservation.getReservedAt());
            member.setPaymentDeadline(reservation.getPaymentDeadline()); member.setPayPrice(reservation.getPayPrice());
            member.setCurrency(reservation.getCurrency()); member.setPayment(reservation.getPayment()); members.add(member);
        }
        snapshot.setMembers(members); return snapshot;
    }

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public ReconciliationResult complete(ReconciliationCompleteRequest request) {
        checkScope(request); V2Format.id(request.getTeamId(),64);
        V2Format.id(request.getReconciliationId(),64); V2Format.id(request.getRosterVersion(),64);
        V2Format.id(request.getCompletionId(),64); V2Format.instant(request.getCheckedAt());
        if (request.getResults()==null || request.getResults().size()>100) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        Map<String,Object> before=team(request.getSource(),request.getChannel(),request.getTeamId(),false);
        if (before.get("roster_json")==null) throw new V2Failure(V2Codes.RECONCILIATION_NOT_STARTED);
        List<String> frozen=roster(before);
        Map<String,ReconciliationDecision> decisions=new HashMap<>();
        for (ReconciliationDecision d:request.getResults()) {
            if (d==null) throw new V2Failure(V2Codes.RECONCILIATION_INCOMPLETE);
            V2Format.id(d.getOutTradeNo(),64); V2Format.id(d.getUserId(),64);
            if (decisions.put(d.getOutTradeNo(),d)!=null) throw new V2Failure(V2Codes.RECONCILIATION_INCOMPLETE);
        }
        if (decisions.size()!=frozen.size() || !decisions.keySet().containsAll(frozen))
            throw new V2Failure(V2Codes.RECONCILIATION_INCOMPLETE);
        List<String> sorted=new ArrayList<>(frozen); Collections.sort(sorted);
        // Every payment and close takes business-key guard, order, then team. Keep the same lock order.
        for (String number:sorted) {
            jdbc.update("insert into ab_trade_guard(source,channel,out_trade_no) values(?,?,?) " +
                    "on duplicate key update out_trade_no=values(out_trade_no)",request.getSource(),request.getChannel(),number);
        }
        Map<String,Map<String,Object>> locked=new HashMap<>();
        for (String number:sorted) {
            Map<String,Object> row=first("select * from ab_trade_order where source=? and channel=? and out_trade_no=? for update",
                    request.getSource(),request.getChannel(),number);
            if (row==null) throw new V2Failure(V2Codes.RECONCILIATION_INCOMPLETE);
            locked.put(number,row);
        }
        Map<String,Object> team=team(request.getSource(),request.getChannel(),request.getTeamId(),true);
        if (!same(request.getReconciliationId(),str(team,"reconciliation_id")) ||
                !same(request.getRosterVersion(),str(team,"roster_version")))
            throw new V2Failure(V2Codes.RECONCILIATION_CONFLICT);
        // A retry may reorder the result list, but cannot change any completion fact.
        List<ReconciliationDecision> normalized=new ArrayList<>();
        for (String number:sorted) normalized.add(decisions.get(number));
        Map<String,Object> completionFacts=new LinkedHashMap<>();
        completionFacts.put("checkedAt",request.getCheckedAt());
        completionFacts.put("reconciliationId",request.getReconciliationId());
        completionFacts.put("rosterVersion",request.getRosterVersion());
        completionFacts.put("results",normalized);
        String hash=V2Format.canonicalHash(json,json.valueToTree(completionFacts));
        if ("COMPLETED".equals(str(team,"team_status"))) {
            if (same(request.getCompletionId(),str(team,"completion_id")) &&
                    !same(hash,str(team,"completion_hash"))) throw new V2Failure(V2Codes.RECONCILIATION_CONFLICT);
            if (team.get("completion_id")==null)
                jdbc.update("update ab_team set completion_id=?,completion_hash=? where team_id=?",
                        request.getCompletionId(),hash,request.getTeamId());
            return result(team);
        }
        if ("FAILED".equals(str(team,"team_status"))) {
            if (!same(request.getCompletionId(),str(team,"completion_id")) ||
                    !same(hash,str(team,"completion_hash"))) throw new V2Failure(V2Codes.RECONCILIATION_CONFLICT);
            return result(team);
        }
        if (!"RECONCILING".equals(str(team,"team_status"))) throw new V2Failure(V2Codes.RECONCILIATION_NOT_STARTED);
        for (String number:sorted) {
            Map<String,Object> row=locked.get(number); ReconciliationDecision d=decisions.get(number);
            if (!same(d.getUserId(),str(row,"user_id"))) throw new V2Failure(V2Codes.RECONCILIATION_CONFLICT);
            switch (d.getDisposition()==null?"":d.getDisposition()) {
                case "PAID_ACCEPTED":
                    if (!"PAID".equals(str(row,"order_status")) || !same(d.getPaymentId(),str(row,"payment_id")))
                        throw new V2Failure(V2Codes.RECONCILIATION_INCOMPLETE);
                    break;
                case "CLOSED_UNPAID":
                    if (!"CANCELLED".equals(str(row,"order_status")) || !same(d.getCloseId(),str(row,"close_id")))
                        throw new V2Failure(V2Codes.RECONCILIATION_INCOMPLETE);
                    break;
                case "PAID_OUTSIDE_WINDOW":
                    outsideWindow(request,row,d);
                    break;
                default: throw new V2Failure(V2Codes.RECONCILIATION_INCOMPLETE);
            }
        }
        team=team(request.getSource(),request.getChannel(),request.getTeamId(),false);
        if (num(team,"paid_count")>=num(team,"target_count")) throw new V2Failure(V2Codes.RECONCILIATION_CONFLICT);
        jdbc.update("update ab_team set team_status='FAILED',team_version=team_version+1,completion_id=?," +
                "completion_hash=?,resolved_at=now(3) where team_id=?",request.getCompletionId(),hash,request.getTeamId());
        List<String> refunds=jdbc.query("select out_trade_no from ab_trade_order where team_id=? and order_status='PAID' order by out_trade_no",
                (rs,n)->rs.getString(1),request.getTeamId());
        TeamFailed event=new TeamFailed(); event.setTeamId(request.getTeamId());
        event.setActivityId(str(team,"activity_id")); event.setTeamVersion(Long.toString(num(team,"team_version")+1));
        event.setOutTradeNoList(frozen); event.setReconciliationId(request.getReconciliationId());
        event.setReason("TARGET_NOT_REACHED"); event.setRefundRequiredOutTradeNoList(refunds);
        outbox.enqueue("A_TEAM_EVENTS","TEAM_FAILED",request.getSource(),request.getChannel(),null,event);
        return result(team(request.getSource(),request.getChannel(),request.getTeamId(),false));
    }

    private void outsideWindow(ReconciliationCompleteRequest request,Map<String,Object> row,ReconciliationDecision d) {
        V2Format.id(d.getPaymentId(),64); V2Format.id(d.getRefundId(),64);
        V2Format.currency(d.getCurrency()); BigDecimal amount=V2Format.money(d.getPaidAmount());
        Instant paidAt=V2Format.instant(d.getPaidAt());
        if ("PAID".equals(str(row,"order_status")) ||
                (!paidAt.isBefore(time(row,"reserved_at").toInstant()) &&
                        paidAt.isBefore(time(row,"payment_deadline").toInstant())) ||
                paidAt.isAfter(jdbc.queryForObject("select now(3)",Timestamp.class).toInstant()))
            throw new V2Failure(V2Codes.RECONCILIATION_CONFLICT);
        Map<String,Object> exceptional=first("select * from ab_exception_payment where source=? and channel=? and out_trade_no=?",
                request.getSource(),request.getChannel(),d.getOutTradeNo());
        if (exceptional!=null && (!same(d.getPaymentId(),str(exceptional,"payment_id")) ||
                amount.compareTo((BigDecimal)exceptional.get("paid_amount"))!=0 ||
                !paidAt.equals(time(exceptional,"paid_at").toInstant()) ||
                !same(d.getRefundId(),str(exceptional,"refund_id"))))
            throw new V2Failure(V2Codes.RECONCILIATION_CONFLICT);
        identity.bind(request.getSource(),request.getChannel(),d.getPaymentId(),d.getOutTradeNo());
        if (!"CANCELLED".equals(str(row,"order_status"))) {
            jdbc.update("update ab_trade_order set order_status='CANCELLED',record_version=record_version+1," +
                            "closed_at=now(3) where source=? and channel=? and out_trade_no=?",
                    request.getSource(),request.getChannel(),d.getOutTradeNo());
            jdbc.update("update ab_team set reserved_count=reserved_count-1,team_version=team_version+1 " +
                    "where team_id=? and reserved_count>0",request.getTeamId());
        }
        if (exceptional==null) jdbc.update("insert into ab_exception_payment(source,channel,out_trade_no,payment_id,paid_amount,paid_at,refund_id) " +
                "values(?,?,?,?,?,?,?)",
                request.getSource(),request.getChannel(),d.getOutTradeNo(),d.getPaymentId(),amount,Timestamp.from(paidAt),d.getRefundId());
    }

    private ReconciliationResult result(Map<String,Object> team) {
        ReconciliationResult r=new ReconciliationResult(); r.setTeamId(str(team,"team_id"));
        r.setReconciliationId(str(team,"reconciliation_id")); r.setTeamStatus(str(team,"team_status"));
        r.setTeamVersion(str(team,"team_version")); r.setResolvedAt(V2Format.utc(time(team,"resolved_at")));
        return r;
    }
}
