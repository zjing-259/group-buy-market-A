package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

@Service
public class V2EventEngine {
    @Resource private JdbcTemplate jdbc;
    @Resource private ObjectMapper json;
    @Resource private V2OutboxService outbox;
    @Resource private V2PaymentEngine payments;
    @Resource private V2PaymentIdentity identity;
    private final ObjectMapper inputJson=new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private Map<String,Object> first(String sql,Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args); return rows.isEmpty()?null:rows.get(0);
    }
    private static String str(Map<String,Object> row,String key) { return row.get(key)==null?null:row.get(key).toString(); }

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void quarantine(String eventId,String reason,String raw) {
        if (raw==null) raw="";
        if (eventId!=null && eventId.length()>64) eventId=eventId.substring(0,64);
        jdbc.update("insert into ab_quarantine(event_id,reason,body_hash,body) values(?,?,?,?)",
                eventId,reason,V2Format.sha256(raw),raw.substring(0,Math.min(raw.length(),262144)));
    }

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public EventProcessingResult process(String raw,String authenticatedProducer) {
        if (raw==null || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>256*1024) {
            quarantine(null,"INVALID_SIZE",raw); return null;
        }
        JsonNode root;
        try { root=inputJson.readTree(raw); }
        catch (Exception e) { quarantine(null,"INVALID_JSON",raw); return null; }
        if (root==null || !root.isObject()) { quarantine(null,"INVALID_ENVELOPE",raw); return null; }
        for (String field:Arrays.asList("eventId","eventType","producer","occurredAt","source","channel")) {
            if (!root.path(field).isTextual()) {quarantine(null,"INVALID_ENVELOPE",raw); return null;}
        }
        if (!root.path("schemaVersion").isInt() ||
                (root.hasNonNull("correlationId") && !root.path("correlationId").isTextual())) {
            quarantine(root.path("eventId").asText(null),"INVALID_ENVELOPE",raw); return null;
        }
        EventEnvelope event;
        try { event=inputJson.treeToValue(root,EventEnvelope.class); }
        catch (Exception e) { quarantine(root.path("eventId").asText(null),"INVALID_ENVELOPE",raw); return null; }
        if (event.getEventId()==null || event.getEventType()==null || event.getProducer()==null ||
                event.getSource()==null || event.getChannel()==null || event.getOccurredAt()==null ||
                !Objects.equals(event.getSchemaVersion(),1) || !Objects.equals(event.getProducer(),authenticatedProducer)) {
            quarantine(event.getEventId(),"UNKNOWN_SCHEMA_OR_IDENTITY",raw); return null;
        }
        try {
            V2Format.id(event.getEventId(),64); V2Format.id(event.getProducer(),64);
            V2Format.id(event.getSource(),8); V2Format.id(event.getChannel(),8);
            V2Format.instant(event.getOccurredAt());
            V2Format.optionalId(event.getCorrelationId(),64);
        } catch (V2Failure invalid) { quarantine(event.getEventId(),"INVALID_ENVELOPE",raw); return null; }
        if (!Arrays.asList("PRODUCT_SNAPSHOT_CHANGED","PAYMENT_SUCCEEDED","REFUND_STATUS_CHANGED").contains(event.getEventType())) {
            quarantine(event.getEventId(),"UNKNOWN_EVENT_TYPE",raw); return null;
        }
        String consumer="PRODUCT_SNAPSHOT_CHANGED".equals(event.getEventType())?"A_PRODUCT_SYNC":"A_TRADE_FACTS";
        String hash=V2Format.sha256(raw);
        jdbc.update("insert into ab_inbox(consumer,producer,event_id,source,channel,event_type,body_hash,processing_status) " +
                "values(?,?,?,?,?,?,?,'WAITING_DEPENDENCY') on duplicate key update event_id=values(event_id)",
                consumer,event.getProducer(),event.getEventId(),event.getSource(),event.getChannel(),event.getEventType(),hash);
        Map<String,Object> prior=first("select * from ab_inbox where consumer=? and producer=? and event_id=? for update",
                consumer,event.getProducer(),event.getEventId());
        if (prior!=null && !hash.equals(str(prior,"body_hash"))) {
            quarantine(event.getEventId(),"EVENT_ID_BODY_MISMATCH",raw); return null;
        }
        if (prior!=null && "PROCESSED".equals(str(prior,"processing_status"))) return toResult(prior);
        String outcome="APPLIED",code=V2Codes.SUCCESS,info="SUCCESS";
        Object result=null;
        // Retain the inbox identity while rolling back all partial business writes on rejection or waiting.
        TransactionStatus transaction=TransactionAspectSupport.currentTransactionStatus();
        Object businessSavepoint=transaction.createSavepoint();
        try {
            JsonNode payload=root.path("payload");
            if (!payload.isObject()) throw new V2Failure(V2Codes.INVALID_PARAMETER);
            // Incoming fact DTOs contain only textual fields; do not silently coerce numeric IDs or money.
            Iterator<JsonNode> values=payload.elements();
            while (values.hasNext()) {
                JsonNode value=values.next();
                if (!value.isTextual() && !value.isNull()) throw new V2Failure(V2Codes.INVALID_PARAMETER);
            }
            switch(event.getEventType()) {
                case "PRODUCT_SNAPSHOT_CHANGED": {
                    ProductSnapshotChanged fact=inputJson.treeToValue(payload,ProductSnapshotChanged.class);
                    V2PaymentEngine.Applied applied=product(event,fact,payload);
                    outcome=applied.outcome; result=applied.result; break;
                }
                case "PAYMENT_SUCCEEDED": {
                    V2PaymentEngine.Applied applied=payments.apply(event,inputJson.treeToValue(payload,PaymentSucceeded.class));
                    outcome=applied.outcome; result=applied.result; break;
                }
                case "REFUND_STATUS_CHANGED": {
                    V2PaymentEngine.Applied applied=refund(event,inputJson.treeToValue(payload,RefundStatusChanged.class),payload);
                    outcome=applied.outcome; result=applied.result; break;
                }
                default: throw new IllegalStateException("validated event type changed");
            }
        } catch (V2Wait waiting) {
            transaction.rollbackToSavepoint(businessSavepoint);
            jdbc.update("insert into ab_waiting_dependency(event_id,consumer,producer,body,body_hash,next_check_at) " +
                    "values(?,?,?,?,?,date_add(now(3),interval 30 second)) on duplicate key update " +
                    "attempts=attempts+1,next_check_at=date_add(now(3),interval 30 second)",
                    event.getEventId(),consumer,event.getProducer(),raw,hash);
            return toResult(first("select * from ab_inbox where consumer=? and producer=? and event_id=?",
                    consumer,event.getProducer(),event.getEventId()));
        } catch (V2Failure rejection) {
            transaction.rollbackToSavepoint(businessSavepoint);
            outcome="REJECTED"; code=rejection.getCode(); info=code; result=null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            transaction.rollbackToSavepoint(businessSavepoint);
            outcome="REJECTED"; code=V2Codes.INVALID_PARAMETER; info=code; result=null;
        } finally {
            transaction.releaseSavepoint(businessSavepoint);
        }
        String processedAt=V2Format.now(); String resultJson;
        try { resultJson=result==null?null:json.writeValueAsString(result); }
        catch (Exception e) { throw new IllegalStateException(e); }
        jdbc.update("update ab_inbox set processing_status='PROCESSED',outcome=?,result_code=?,result_info=?," +
                "result_json=?,processed_at=now(3) where consumer=? and producer=? and event_id=?",
                outcome,code,info,resultJson,consumer,event.getProducer(),event.getEventId());
        jdbc.update("delete from ab_waiting_dependency where consumer=? and producer=? and event_id=?",
                consumer,event.getProducer(),event.getEventId());
        EventProcessingResultPayload reply=new EventProcessingResultPayload();
        reply.setOriginalEventId(event.getEventId()); reply.setOriginalEventType(event.getEventType());
        reply.setOutcome(outcome); reply.setCode(code); reply.setInfo(info); reply.setProcessedAt(processedAt); reply.setResult(result);
        outbox.enqueue("A_INTEGRATION_RESULTS","EVENT_PROCESSING_RESULT",event.getSource(),event.getChannel(),event.getEventId(),reply);
        return toResult(first("select * from ab_inbox where consumer=? and producer=? and event_id=?",
                consumer,event.getProducer(),event.getEventId()));
    }

    private V2PaymentEngine.Applied product(EventEnvelope envelope,ProductSnapshotChanged fact,JsonNode payload) {
        V2Format.id(fact.getGoodsId(),64); V2Format.id(fact.getGoodsName(),128);
        long version=V2Format.positiveVersion(fact.getProductVersion());
        V2Format.currency(fact.getCurrency()); BigDecimal price=V2Format.money(fact.getOriginalPrice());
        if (price.signum()==0 || !Arrays.asList("ON_SALE","OFF_SALE").contains(fact.getSaleStatus()))
            throw new V2Failure(V2Codes.INVALID_PARAMETER);
        String hash=V2Format.canonicalHash(json,json.valueToTree(fact));
        Map<String,Object> old=first("select * from ab_product_projection where source=? and channel=? and goods_id=? for update",
                envelope.getSource(),envelope.getChannel(),fact.getGoodsId());
        boolean applied=false;
        if (old==null) {
            jdbc.update("insert into ab_product_projection(source,channel,goods_id,product_version,goods_name," +
                    "original_price,currency,sale_status,content_hash,updated_at) values(?,?,?,?,?,?,?,?,?,now(3))",
                    envelope.getSource(),envelope.getChannel(),fact.getGoodsId(),version,fact.getGoodsName(),price,
                    fact.getCurrency(),fact.getSaleStatus(),hash);
            applied=true;
        } else if (version>((Number)old.get("product_version")).longValue()) {
            jdbc.update("update ab_product_projection set product_version=?,goods_name=?,original_price=?,currency=?," +
                    "sale_status=?,content_hash=?,updated_at=now(3) where source=? and channel=? and goods_id=?",
                    version,fact.getGoodsName(),price,fact.getCurrency(),fact.getSaleStatus(),hash,
                    envelope.getSource(),envelope.getChannel(),fact.getGoodsId());
            applied=true;
        } else if (version==((Number)old.get("product_version")).longValue() && !hash.equals(str(old,"content_hash"))) {
            throw new V2Failure(V2Codes.PRODUCT_VERSION_CONFLICT);
        }
        Map<String,Object> current=first("select product_version,sale_status from ab_product_projection where source=? and channel=? and goods_id=?",
                envelope.getSource(),envelope.getChannel(),fact.getGoodsId());
        Map<String,Object> result=new LinkedHashMap<>(); result.put("goodsId",fact.getGoodsId());
        result.put("currentVersion",str(current,"product_version")); result.put("applied",applied);
        result.put("saleStatus",str(current,"sale_status"));
        return new V2PaymentEngine.Applied(applied?"APPLIED":"IGNORED",result);
    }

    private V2PaymentEngine.Applied refund(EventEnvelope envelope,RefundStatusChanged fact,JsonNode payload) {
        V2Format.id(fact.getUserId(),64); V2Format.id(fact.getOutTradeNo(),64);
        V2Format.id(fact.getPaymentId(),64); V2Format.id(fact.getRefundId(),64);
        V2Format.instant(fact.getOccurredAt());
        long version=V2Format.positiveVersion(fact.getRefundVersion());
        V2Format.currency(fact.getCurrency()); BigDecimal amount=V2Format.money(fact.getRefundAmount());
        if (!Arrays.asList("PENDING","FAILED","SUCCEEDED").contains(fact.getRefundStatus()))
            throw new V2Failure(V2Codes.INVALID_PARAMETER);
        jdbc.update("insert into ab_trade_guard(source,channel,out_trade_no) values(?,?,?) " +
                "on duplicate key update out_trade_no=values(out_trade_no)",envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        Map<String,Object> order=first("select * from ab_trade_order where source=? and channel=? and out_trade_no=? for update",
                envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        if (order==null) throw new V2Wait("ORDER_NOT_YET_RESERVED");
        if (!fact.getUserId().equals(str(order,"user_id"))) throw new V2Failure("E0112");
        Map<String,Object> team=order.get("team_id")==null?null:first("select * from ab_team where team_id=? for update",str(order,"team_id"));
        Map<String,Object> exceptional=first("select * from ab_exception_payment where source=? and channel=? and out_trade_no=?",
                envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        if (exceptional==null && team!=null && "COMPLETED".equals(str(team,"team_status")) &&
                "PAID".equals(str(order,"order_status"))) throw new V2Failure(V2Codes.REFUND_CONFLICT);
        BigDecimal pendingPaid=null;
        Timestamp pendingPaidAt=null;
        if (exceptional==null) {
            if ("CANCELLED".equals(str(order,"order_status")) && fact.getPaidAt()!=null && fact.getPaidAmount()!=null) {
                pendingPaid=V2Format.money(fact.getPaidAmount());
                pendingPaidAt=Timestamp.from(V2Format.instant(fact.getPaidAt()));
                if (pendingPaidAt.after(jdbc.queryForObject("select now(3)",Timestamp.class)))
                    throw new V2Failure(V2Codes.REFUND_CONFLICT);
            } else if ("CANCELLED".equals(str(order,"order_status")) ||
                    !"FAILED".equals(team==null?null:str(team,"team_status")))
                throw new V2Wait("REFUND_EVIDENCE_NOT_YET_AVAILABLE");
        }
        String paymentId=exceptional!=null?str(exceptional,"payment_id"):
                pendingPaid!=null?fact.getPaymentId():str(order,"payment_id");
        BigDecimal paid=exceptional!=null?(BigDecimal)exceptional.get("paid_amount"):
                pendingPaid!=null?pendingPaid:(BigDecimal)order.get("paid_amount");
        if (!fact.getPaymentId().equals(paymentId) || paid==null || amount.compareTo(paid)!=0)
            throw new V2Failure(V2Codes.REFUND_CONFLICT);
        if (exceptional!=null && !fact.getRefundId().equals(str(exceptional,"refund_id")))
            throw new V2Failure(V2Codes.REFUND_CONFLICT);
        if (exceptional!=null && (fact.getPaidAmount()!=null || fact.getPaidAt()!=null)) {
            if (fact.getPaidAmount()==null || fact.getPaidAt()==null ||
                    V2Format.money(fact.getPaidAmount()).compareTo(paid)!=0 ||
                    !V2Format.instant(fact.getPaidAt()).equals(((Timestamp)exceptional.get("paid_at")).toInstant()))
                throw new V2Failure(V2Codes.REFUND_CONFLICT);
        }
        String hash=V2Format.canonicalHash(json,json.valueToTree(fact));
        long current=order.get("refund_version")==null?0:((Number)order.get("refund_version")).longValue();
        if (current==version && !hash.equals(str(order,"refund_hash"))) throw new V2Failure(V2Codes.REFUND_CONFLICT);
        if (order.get("refund_id")!=null && !fact.getRefundId().equals(str(order,"refund_id")))
            throw new V2Failure(V2Codes.REFUND_CONFLICT);
        if ("SUCCEEDED".equals(str(order,"refund_status")) && version>current &&
                !"SUCCEEDED".equals(fact.getRefundStatus()))
            throw new V2Failure(V2Codes.REFUND_CONFLICT);
        identity.bind(envelope.getSource(),envelope.getChannel(),fact.getPaymentId(),fact.getOutTradeNo());
        if (pendingPaid!=null) {
            jdbc.update("insert into ab_exception_payment(source,channel,out_trade_no,payment_id,paid_amount,paid_at,refund_id) " +
                    "values(?,?,?,?,?,?,?)",envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo(),
                    fact.getPaymentId(),pendingPaid,pendingPaidAt,fact.getRefundId());
        }
        boolean applied=version>current;
        if (applied) jdbc.update("update ab_trade_order set refund_id=?,refund_status=?,refund_version=?,refund_amount=?," +
                        "refund_hash=?,record_version=record_version+1 where source=? and channel=? and out_trade_no=?",
                fact.getRefundId(),fact.getRefundStatus(),version,amount,hash,envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        Map<String,Object> latest=first("select refund_id,refund_status,refund_version,record_version from ab_trade_order " +
                "where source=? and channel=? and out_trade_no=?",envelope.getSource(),envelope.getChannel(),fact.getOutTradeNo());
        Map<String,Object> result=new LinkedHashMap<>(); result.put("outTradeNo",fact.getOutTradeNo());
        result.put("refundId",str(latest,"refund_id")); result.put("refundStatus",str(latest,"refund_status"));
        result.put("refundVersion",str(latest,"refund_version")); result.put("recordVersion",str(latest,"record_version"));
        return new V2PaymentEngine.Applied(applied?"APPLIED":"IGNORED",result);
    }

    private EventProcessingResult toResult(Map<String,Object> row) {
        EventProcessingResult result=new EventProcessingResult();
        result.setEventId(str(row,"event_id")); result.setEventType(str(row,"event_type"));
        result.setProcessingStatus(str(row,"processing_status")); result.setOutcome(str(row,"outcome"));
        result.setCode(str(row,"result_code")); result.setInfo(str(row,"result_info"));
        result.setProcessedAt(V2Format.utc((Timestamp)row.get("processed_at")));
        if (row.get("result_json")!=null) {
            try { result.setResult(json.readValue(str(row,"result_json"),Object.class)); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        return result;
    }

    public EventProcessingResult query(EventResultQueryRequest request,String producer) {
        V2Format.id(request.getSource(),8); V2Format.id(request.getChannel(),8); V2Format.id(request.getEventId(),64);
        Map<String,Object> row=first("select * from ab_inbox where producer=? and event_id=? " +
                "and source=? and channel=?",producer,request.getEventId(),request.getSource(),request.getChannel());
        if (row==null) throw new V2Failure(V2Codes.NOT_FOUND);
        return toResult(row);
    }
}
