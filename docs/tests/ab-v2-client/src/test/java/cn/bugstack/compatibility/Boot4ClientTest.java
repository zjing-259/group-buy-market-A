package cn.bugstack.compatibility;

import cn.bugstack.api.v2.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.rocketmq.acl.common.*;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent B-shaped process: Boot 4, Java 17 bytecode, raw Dubbo/MQ SDKs, shared Java 8 DTOs. */
public class Boot4ClientTest {
    @SpringBootConfiguration @EnableAutoConfiguration public static class BApplication { }
    private static ConfigurableApplicationContext boot;
    private final ObjectMapper json=new ObjectMapper().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true).setSerializationInclusion(JsonInclude.Include.ALWAYS);

    @BeforeAll static void start() {
        assertNotNull(System.getenv("AB_V2_RPC_SECRET"),"Load the B integration environment first");
        boot=new SpringApplicationBuilder(BApplication.class).web(WebApplicationType.NONE).run();
    }
    @AfterAll static void stop() {if (boot!=null) boot.close();}

    private static String sha(byte[] body) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(body); StringBuilder text=new StringBuilder();
        for (byte value:hash) text.append(String.format("%02x",value & 255)); return text.toString();
    }
    private static String hmac(String secret,String text) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }
    private ReferenceConfig<IntegrationService> reference() {
        return reference(IntegrationService.class);
    }
    private <T> ReferenceConfig<T> reference(Class<T> type) {
        ReferenceConfig<T> r=new ReferenceConfig<>();
        r.setApplication(new ApplicationConfig("boot4-v2-client")); r.setInterface(type);
        r.setUrl("tri://127.0.0.1:50051"); r.setVersion("2.0.0"); r.setGroup("gbm-dev"); r.setRetries(0); r.setTimeout(10000);
        String certificate=System.getenv("AB_V2_TEST_TLS_CERT");
        if (certificate!=null) {
            org.apache.dubbo.config.SslConfig ssl=new org.apache.dubbo.config.SslConfig();
            ssl.setClientKeyCertChainPath(certificate); ssl.setClientTrustCertCollectionPath(certificate);
            ssl.setClientPrivateKeyPath(System.getenv("AB_V2_TEST_TLS_KEY"));
            r.getApplicationModel().getApplicationConfigManager().setSsl(ssl);
            r.setUrl("tri://127.0.0.1:50051?ssl-enabled=true");
        }
        return r;
    }
    private RpcResult<EventProcessingResult> query(IntegrationService service,String eventId,boolean valid) throws Exception {
        EventResultQueryRequest dto=new EventResultQueryRequest(); dto.setSource("s01"); dto.setChannel("c01"); dto.setEventId(eventId);
        String requestId=sign(dto,IntegrationService.class.getName(),"queryEventResult",valid);
        RpcResult<EventProcessingResult> result=service.queryEventResult(dto); assertEquals(requestId,result.getRequestId()); return result;
    }
    private String sign(BaseRequest dto,String interfaceName,String method,boolean valid) throws Exception {
        String client=System.getenv("AB_V2_RPC_CLIENT_ID"),key=System.getenv("AB_V2_RPC_KEY_ID");
        String timestamp=Long.toString(System.currentTimeMillis()),requestId=UUID.randomUUID().toString();
        String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        Map<String,String> attachments=new HashMap<>(); attachments.put("clientId",client); attachments.put("keyId",key);
        attachments.put("timestamp",timestamp); attachments.put("requestId",requestId); attachments.put("nonce",nonce);
        String text=String.join("\n",client,key,timestamp,nonce,requestId,interfaceName,method,
                "2.0.0","gbm-dev",sha(json.writeValueAsBytes(dto)));
        attachments.put("signature",valid?hmac(System.getenv("AB_V2_RPC_SECRET"),text):"invalid");
        attachments.forEach((name,value)->RpcContext.getClientAttachment().setAttachment(name,value));
        return requestId;
    }

    private String utcNow() {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.now());
    }
    private void sql(java.sql.Connection db,String text,Object... values) throws Exception {
        try (java.sql.PreparedStatement statement=db.prepareStatement(text)) {
            for (int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); statement.executeUpdate();
        }
    }
    private DefaultMQProducer producer() throws Exception {
        DefaultMQProducer p=new DefaultMQProducer("B_FLOW_"+UUID.randomUUID(),new AclClientRPCHook(new SessionCredentials(
                System.getenv("AB_V2_ROCKETMQ_ACCESS_KEY"),System.getenv("AB_V2_ROCKETMQ_SECRET_KEY"))));
        p.setNamesrvAddr("127.0.0.1:9876"); p.setRetryTimesWhenSendFailed(0); p.start(); return p;
    }
    private void publishAndConfirm(DefaultMQProducer producer,IntegrationService service,String kind,Object payload) throws Exception {
        String id="flow-"+UUID.randomUUID(); String topic=kind.equals("PRODUCT_SNAPSHOT_CHANGED")?"B_PRODUCT_EVENTS-dev":"B_TRADE_EVENTS-dev";
        EventEnvelope event=new EventEnvelope(); event.setEventId(id); event.setEventType(kind); event.setSchemaVersion(1);
        event.setProducer(System.getenv("AB_V2_RPC_CLIENT_ID")); event.setOccurredAt(utcNow()); event.setSource("s01"); event.setChannel("c01"); event.setPayload(payload);
        byte[] body=json.writeValueAsBytes(event); Message message=new Message(topic,kind,id,body);
        message.putUserProperty("source","s01"); message.putUserProperty("channel","c01");
        message.putUserProperty("keyId",System.getenv("AB_V2_MQ_B_KEY_ID"));
        message.putUserProperty("signature",hmac(System.getenv("AB_V2_MQ_B_SECRET"),topic+"\n"+sha(body)));
        assertEquals(SendStatus.SEND_OK,producer.send(message,10000).getSendStatus());
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime()<deadline) {
            RpcResult<EventProcessingResult> result=query(service,id,true);
            if ("0000".equals(result.getCode()) && "PROCESSED".equals(result.getData().getProcessingStatus())) {
                assertEquals("APPLIED",result.getData().getOutcome(),result.getData().getCode()); return;
            }
            Thread.sleep(250);
        }
        fail("No applied fact within 30 seconds: "+kind);
    }

    @Test void realSignedReservationPaymentReconciliationAndRefundFlow() throws Exception {
        String goods="flow-"+UUID.randomUUID(),number="flow-"+UUID.randomUUID();
        long activity=1_000_000_000L+new java.security.SecureRandom().nextInt(1_000_000_000);
        // The legacy activity mapper treats discount IDs as numeric; keep this fixture in its existing format.
        String discount=Long.toString(10_000_000L+new java.security.SecureRandom().nextInt(90_000_000));
        String url=System.getenv("SPRING_DATASOURCE_URL");
        assertNotNull(url); assertTrue(url.contains("/group_buy_market_phase1_test?"),"Only the isolated database is permitted");
        ReferenceConfig<MarketingService> marketing=reference(MarketingService.class);
        ReferenceConfig<TradeService> trade=reference(TradeService.class);
        ReferenceConfig<TeamService> team=reference(TeamService.class);
        ReferenceConfig<IntegrationService> integration=reference();
        DefaultMQProducer producer=null;
        try (java.sql.Connection db=java.sql.DriverManager.getConnection(url,"root",System.getenv("PHASE1_DB_PASSWORD"))) {
            try {
                sql(db,"insert into group_buy_discount(discount_id,discount_name,discount_desc,discount_type,market_plan,market_expr) values(?,'Flow','Flow',0,'ZJ','20')",discount);
                sql(db,"insert into group_buy_activity(activity_id,activity_name,discount_id,group_type,take_limit_count,target,valid_time,status,start_time,end_time) values(?,'Flow',?,1,10,2,10,1,now()-interval 1 hour,now()+interval 1 hour)",activity,discount);
                sql(db,"insert into sc_sku_activity(source,channel,activity_id,goods_id) values('s01','c01',?,?)",activity,goods);
                producer=producer(); IntegrationService results=integration.get();
                ProductSnapshotChanged product=new ProductSnapshotChanged(); product.setGoodsId(goods); product.setProductVersion("1");
                product.setGoodsName("Flow"); product.setOriginalPrice("100.00"); product.setCurrency("CNY"); product.setSaleStatus("ON_SALE");
                publishAndConfirm(producer,results,"PRODUCT_SNAPSHOT_CHANGED",product);
                TrialRequest trial=new TrialRequest(); trial.setSource("s01"); trial.setChannel("c01"); trial.setUserId("flow-owner");
                trial.setGoodsId(goods); trial.setProductVersion("1"); trial.setQuantity(1); trial.setActivityId(Long.toString(activity));
                sign(trial,MarketingService.class.getName(),"trial",true);
                RpcResult<TrialResponse> quoted=marketing.get().trial(trial); assertEquals("0000",quoted.getCode());
                assertTrue(quoted.getData().getEligible()); assertEquals("80.00",quoted.getData().getOffer().getPayPrice());
                ReserveRequest reserve=new ReserveRequest(); reserve.setSource("s01"); reserve.setChannel("c01"); reserve.setUserId("flow-owner");
                reserve.setOutTradeNo(number); reserve.setGoodsId(goods); reserve.setActivityId(Long.toString(activity));
                reserve.setProductVersion("1"); reserve.setQuantity(1); reserve.setExpectedPayPrice("80.00"); reserve.setCurrency("CNY");
                TradeService trades=trade.get(); TeamService teams=team.get();
                sign(reserve,TradeService.class.getName(),"reserve",true); RpcResult<Reservation> reserved=trades.reserve(reserve);
                assertEquals("0000",reserved.getCode()); String teamId=reserved.getData().getTeamId();
                sign(reserve,TradeService.class.getName(),"reserve",true);
                assertEquals(reserved.getData().getOrderId(),trades.reserve(reserve).getData().getOrderId());
                PaymentSucceeded paid=new PaymentSucceeded(); paid.setUserId("flow-owner"); paid.setOutTradeNo(number);
                paid.setPaymentId("payment-"+UUID.randomUUID()); paid.setPaidAmount("80.00"); paid.setCurrency("CNY"); paid.setPaidAt(utcNow());
                publishAndConfirm(producer,results,"PAYMENT_SUCCEEDED",paid);
                TradeQueryRequest order=new TradeQueryRequest(); order.setSource("s01"); order.setChannel("c01"); order.setUserId("flow-owner"); order.setOutTradeNo(number);
                sign(order,TradeService.class.getName(),"query",true); assertEquals("PAID",trades.query(order).getData().getOrderStatus());
                // Accelerate only this unique test team's deadline; production transition still runs through A's scheduler.
                sql(db,"update ab_team set valid_end_time=now(3) where team_id=?",teamId);
                ReconciliationQueryRequest rq=new ReconciliationQueryRequest(); rq.setSource("s01"); rq.setChannel("c01"); rq.setTeamId(teamId);
                ReconciliationSnapshot snapshot=null; long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                while (System.nanoTime()<deadline) {
                    sign(rq,TeamService.class.getName(),"queryReconciliation",true);
                    RpcResult<ReconciliationSnapshot> result=teams.queryReconciliation(rq);
                    if ("0000".equals(result.getCode())) {snapshot=result.getData(); break;}
                    assertEquals("RECONCILIATION_NOT_STARTED",result.getCode()); Thread.sleep(250);
                }
                assertNotNull(snapshot); assertEquals(1,snapshot.getMembers().size());
                ReconciliationDecision decision=new ReconciliationDecision(); decision.setUserId("flow-owner"); decision.setOutTradeNo(number);
                decision.setDisposition("PAID_ACCEPTED"); decision.setPaymentId(paid.getPaymentId());
                ReconciliationCompleteRequest rc=new ReconciliationCompleteRequest(); rc.setSource("s01"); rc.setChannel("c01"); rc.setTeamId(teamId);
                rc.setReconciliationId(snapshot.getReconciliationId()); rc.setRosterVersion(snapshot.getRosterVersion());
                rc.setCompletionId(UUID.randomUUID().toString()); rc.setCheckedAt(utcNow()); rc.setResults(Collections.singletonList(decision));
                sign(rc,TeamService.class.getName(),"completeReconciliation",true);
                RpcResult<ReconciliationResult> failed=teams.completeReconciliation(rc); assertEquals("0000",failed.getCode()); assertEquals("FAILED",failed.getData().getTeamStatus());
                RefundStatusChanged refund=new RefundStatusChanged(); refund.setUserId("flow-owner"); refund.setOutTradeNo(number);
                refund.setPaymentId(paid.getPaymentId()); refund.setRefundId("refund-"+UUID.randomUUID()); refund.setRefundAmount("80.00");
                refund.setCurrency("CNY"); refund.setRefundStatus("SUCCEEDED"); refund.setRefundVersion("1"); refund.setOccurredAt(utcNow());
                publishAndConfirm(producer,results,"REFUND_STATUS_CHANGED",refund);
                sign(order,TradeService.class.getName(),"query",true); RpcResult<Reservation> finalOrder=trades.query(order);
                assertEquals("PAID",finalOrder.getData().getOrderStatus()); assertEquals("FAILED",finalOrder.getData().getTeamStatus());
                assertEquals("SUCCEEDED",finalOrder.getData().getRefund().getRefundStatus());
                TeamQueryRequest tq=new TeamQueryRequest(); tq.setSource("s01"); tq.setChannel("c01"); tq.setTeamId(teamId); tq.setUserId("flow-owner");
                sign(tq,TeamService.class.getName(),"query",true); assertEquals(number,teams.query(tq).getData().getOwnReservation().getOutTradeNo());
            } finally {
                sql(db,"delete from sc_sku_activity where activity_id=?",activity);
                sql(db,"delete from group_buy_activity where activity_id=?",activity);
                sql(db,"delete from group_buy_discount where discount_id=?",discount);
            }
        } finally {if (producer!=null) producer.shutdown(); marketing.destroy(); trade.destroy(); team.destroy(); integration.destroy();}
    }

    @Test void boot4TripleSerializesSharedDtosAndAuthResults() throws Exception {
        assertTrue(boot.isActive()); ReferenceConfig<IntegrationService> r=reference();
        try {IntegrationService service=r.get(); assertEquals("NOT_FOUND",query(service,"missing-"+UUID.randomUUID(),true).getCode());
            assertEquals("AUTH_INVALID",query(service,"missing-"+UUID.randomUUID(),false).getCode());}
        finally {r.destroy();}
    }

    @Test void tlsProviderRejectsPlaintextConnection() throws Exception {
        Assumptions.assumeTrue(System.getenv("AB_V2_TEST_TLS_CERT")!=null);
        try (java.net.Socket socket=new java.net.Socket("127.0.0.1",50051)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush(); assertEquals(-1,socket.getInputStream().read(),"plaintext must be rejected");
        }
    }

    @Test void signedCloseQueryAndRemainingTeamMethodsUseSharedDtos() throws Exception {
        ReferenceConfig<TradeService> trade=reference(TradeService.class);
        ReferenceConfig<TeamService> team=reference(TeamService.class);
        try {
            TradeService trades=trade.get(); TeamService teams=team.get();
            String number="closed-"+UUID.randomUUID();
            CloseRequest close=new CloseRequest(); close.setSource("s01"); close.setChannel("c01");
            close.setUserId("compat-owner"); close.setOutTradeNo(number); close.setCloseId(UUID.randomUUID().toString());
            close.setReason("ORDER_ABORT"); close.setPaymentDisposition("NEVER_OPENED");
            close.setVerifiedAt(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.now()));
            sign(close,TradeService.class.getName(),"close",true);
            RpcResult<CloseResponse> closed=trades.close(close); assertEquals("0000",closed.getCode());
            assertEquals("CANCELLED",closed.getData().getOrderStatus()); assertNull(closed.getData().getOrderId());
            sign(close,TradeService.class.getName(),"close",true);
            RpcResult<CloseResponse> replay=trades.close(close); assertEquals("0000",replay.getCode());
            assertEquals(closed.getData().getRecordVersion(),replay.getData().getRecordVersion());
            TradeQueryRequest query=new TradeQueryRequest(); query.setSource("s01"); query.setChannel("c01");
            query.setUserId("compat-owner"); query.setOutTradeNo(number);
            sign(query,TradeService.class.getName(),"query",true);
            RpcResult<Reservation> found=trades.query(query); assertEquals("0000",found.getCode());
            assertEquals("CLOSE_MARKER",found.getData().getRecordKind()); assertEquals("CANCELLED",found.getData().getOrderStatus());
            query.setUserId("Compat-owner"); sign(query,TradeService.class.getName(),"query",true);
            assertEquals("FORBIDDEN",trades.query(query).getCode());
            ReserveRequest reserve=new ReserveRequest(); reserve.setSource("s01"); reserve.setChannel("c01");
            reserve.setUserId("compat-owner"); reserve.setOutTradeNo(number); reserve.setGoodsId("unused");
            reserve.setActivityId("1"); reserve.setProductVersion("1"); reserve.setQuantity(1);
            reserve.setCurrency("CNY"); reserve.setExpectedPayPrice("80.00");
            sign(reserve,TradeService.class.getName(),"reserve",true);
            assertEquals("ORDER_CLOSED",trades.reserve(reserve).getCode());
            String missing="missing-"+UUID.randomUUID();
            TeamQueryRequest tq=new TeamQueryRequest(); tq.setSource("s01"); tq.setChannel("c01"); tq.setTeamId(missing);
            sign(tq,TeamService.class.getName(),"query",true); assertEquals("NOT_FOUND",teams.query(tq).getCode());
            ReconciliationQueryRequest rq=new ReconciliationQueryRequest(); rq.setSource("s01"); rq.setChannel("c01"); rq.setTeamId(missing);
            sign(rq,TeamService.class.getName(),"queryReconciliation",true);
            assertEquals("NOT_FOUND",teams.queryReconciliation(rq).getCode());
            ReconciliationCompleteRequest rc=new ReconciliationCompleteRequest(); rc.setSource("s01"); rc.setChannel("c01");
            rc.setTeamId(missing); rc.setReconciliationId("missing"); rc.setRosterVersion("missing"); rc.setCompletionId("missing");
            rc.setCheckedAt(close.getVerifiedAt()); rc.setResults(Collections.emptyList());
            sign(rc,TeamService.class.getName(),"completeReconciliation",true);
            assertEquals("NOT_FOUND",teams.completeReconciliation(rc).getCode());
        } finally {trade.destroy(); team.destroy();}
    }

    @Test void boot4MqClientPublishesSignedProductAndQueriesDurableResult() throws Exception {
        String eventId="compat-"+UUID.randomUUID();
        String time=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        Map<String,Object> payload=new TreeMap<>(); payload.put("goodsId","compat-"+UUID.randomUUID()); payload.put("productVersion","1");
        payload.put("goodsName","Compatibility"); payload.put("originalPrice","100.00"); payload.put("currency","CNY"); payload.put("saleStatus","ON_SALE");
        Map<String,Object> envelope=new TreeMap<>(); envelope.put("eventId",eventId); envelope.put("eventType","PRODUCT_SNAPSHOT_CHANGED");
        envelope.put("schemaVersion",1); envelope.put("producer",System.getenv("AB_V2_RPC_CLIENT_ID")); envelope.put("occurredAt",time);
        envelope.put("source","s01"); envelope.put("channel","c01"); envelope.put("payload",payload);
        byte[] body=json.writeValueAsBytes(envelope); String topic="B_PRODUCT_EVENTS-dev";
        Message message=new Message(topic,"PRODUCT_SNAPSHOT_CHANGED",eventId,body);
        message.putUserProperty("source","s01"); message.putUserProperty("channel","c01");
        message.putUserProperty("keyId",System.getenv("AB_V2_MQ_B_KEY_ID"));
        message.putUserProperty("signature",hmac(System.getenv("AB_V2_MQ_B_SECRET"),topic+"\n"+sha(body)));
        DefaultMQProducer producer=new DefaultMQProducer("B_COMPAT_"+UUID.randomUUID(),new AclClientRPCHook(new SessionCredentials(
                System.getenv("AB_V2_ROCKETMQ_ACCESS_KEY"),System.getenv("AB_V2_ROCKETMQ_SECRET_KEY"))));
        producer.setNamesrvAddr("127.0.0.1:9876"); producer.setRetryTimesWhenSendFailed(0); producer.start();
        ReferenceConfig<IntegrationService> r=reference();
        try {
            assertEquals(SendStatus.SEND_OK,producer.send(message,10000).getSendStatus()); IntegrationService service=r.get();
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime()<deadline) {
                RpcResult<EventProcessingResult> result=query(service,eventId,true);
                if ("0000".equals(result.getCode())) {assertEquals("PROCESSED",result.getData().getProcessingStatus());
                    assertEquals("APPLIED",result.getData().getOutcome());
                    ReferenceConfig<MarketingService> marketing=reference(MarketingService.class);
                    try {
                        MarketingService serviceMarketing=marketing.get(); TrialRequest trial=new TrialRequest();
                        trial.setSource("s01"); trial.setChannel("c01"); trial.setUserId("compat-user");
                        trial.setGoodsId(payload.get("goodsId").toString()); trial.setProductVersion("1"); trial.setQuantity(1);
                        String requestId=sign(trial,MarketingService.class.getName(),"trial",true);
                        RpcResult<TrialResponse> quoted=serviceMarketing.trial(trial);
                        assertEquals("0000",quoted.getCode()); assertEquals(requestId,quoted.getRequestId());
                        assertFalse(quoted.getData().getAvailable()); assertNull(quoted.getData().getOffer());
                    } finally {marketing.destroy();}
                    return;}
                assertEquals("NOT_FOUND",result.getCode()); Thread.sleep(250);
            }
            fail("No durable A processing result within 30 seconds");
        } finally {r.destroy(); producer.shutdown();}
    }
}
