package cn.bugstack.test.v2;

import cn.bugstack.v2.V2Format;
import cn.bugstack.v2.V2Security;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.Assume;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/** Run explicitly with RocketMQ and the A provider against the isolated MySQL schema. */
public class V2MqSmokeTest {
    @Test public void bCredentialsCannotPublishTeamEvents() throws Exception {
        String accessKey=System.getenv("AB_V2_ROCKETMQ_ACCESS_KEY");
        String brokerSecret=System.getenv("AB_V2_ROCKETMQ_SECRET_KEY");
        Assume.assumeTrue(accessKey!=null && brokerSecret!=null);
        DefaultMQProducer producer=new DefaultMQProducer("B_ACL_SMOKE_"+UUID.randomUUID(),
                new AclClientRPCHook(new SessionCredentials(accessKey,brokerSecret)));
        producer.setNamesrvAddr("127.0.0.1:9876"); producer.setRetryTimesWhenSendFailed(0);
        producer.setSendMsgTimeout(10000);
        producer.start();
        try {
            Message forbidden=new Message("A_TEAM_EVENTS-dev","TEAM_COMPLETED",UUID.randomUUID().toString(),
                    "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Exception last=null;
            for (int attempt=0;attempt<3;attempt++) {
                try { producer.send(forbidden,10000); org.junit.Assert.fail("B published an A-only topic"); }
                catch (Exception denied) {
                    Throwable cause=denied;
                    while (cause!=null) {
                        if (cause instanceof org.apache.rocketmq.client.exception.MQBrokerException &&
                                cause.getMessage().toLowerCase(java.util.Locale.ROOT).contains("permission")) return;
                        cause=cause.getCause();
                    }
                    last=denied;
                }
            }
            throw new AssertionError("No explicit Broker ACL rejection after three attempts",last);
        } finally { producer.shutdown(); }
    }

    @Test public void signedProductEventIsConsumedAndProducesResult() throws Exception {
        String accessKey=System.getenv("AB_V2_ROCKETMQ_ACCESS_KEY");
        String brokerSecret=System.getenv("AB_V2_ROCKETMQ_SECRET_KEY");
        String messageSecret=System.getenv("AB_V2_MQ_B_SECRET");
        String password=System.getenv("PHASE1_DB_PASSWORD");
        Assume.assumeTrue(accessKey!=null && brokerSecret!=null && messageSecret!=null && password!=null);
        String eventId="mq-smoke-"+UUID.randomUUID();
        String goodsId="goods-"+UUID.randomUUID();
        ObjectMapper mapper=new ObjectMapper();
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("goodsId",goodsId); payload.put("productVersion","1");
        payload.put("goodsName","MQ Smoke"); payload.put("originalPrice","100.00");
        payload.put("currency","CNY"); payload.put("saleStatus","ON_SALE");
        Map<String,Object> event=new LinkedHashMap<>();
        event.put("eventId",eventId); event.put("eventType","PRODUCT_SNAPSHOT_CHANGED");
        event.put("schemaVersion",1); event.put("producer","zj-market");
        event.put("occurredAt",V2Format.now()); event.put("source","s01");
        event.put("channel","c01"); event.put("payload",payload);
        byte[] body=mapper.writeValueAsBytes(event);
        String topic="B_PRODUCT_EVENTS-dev";
        Message message=new Message(topic,"PRODUCT_SNAPSHOT_CHANGED",eventId,body);
        message.putUserProperty("keyId",System.getenv("AB_V2_MQ_B_KEY_ID"));
        message.putUserProperty("signature",V2Security.hmac(messageSecret,topic+"\n"+V2Format.sha256(body)));
        message.putUserProperty("source","s01"); message.putUserProperty("channel","c01");
        DefaultMQProducer producer=new DefaultMQProducer("B_SMOKE_"+UUID.randomUUID(),
                new AclClientRPCHook(new SessionCredentials(accessKey,brokerSecret)));
        producer.setNamesrvAddr("127.0.0.1:9876"); producer.start();
        try {
            SendResult sent=producer.send(message,5000);
            assertEquals(SendStatus.SEND_OK,sent.getSendStatus());
        } finally { producer.shutdown(); }
        try (Connection db=DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/group_buy_market_phase1_test?serverTimezone=Asia/Shanghai","root",password)) {
            String sql="select i.outcome,p.original_price,o.status from ab_inbox i " +
                    "join ab_product_projection p on p.source=i.source and p.channel=i.channel and p.goods_id=? " +
                    "join ab_outbox o on o.source=i.source and o.channel=i.channel and o.event_type='EVENT_PROCESSING_RESULT' " +
                    "and o.body like ? where i.event_id=?";
            long end=System.currentTimeMillis()+20000;
            while (System.currentTimeMillis()<end) {
                try (PreparedStatement query=db.prepareStatement(sql)) {
                    query.setString(1,goodsId); query.setString(2,"%"+eventId+"%"); query.setString(3,eventId);
                    try (ResultSet rows=query.executeQuery()) {
                        if (rows.next()) {
                            assertEquals("APPLIED",rows.getString(1));
                            assertEquals("100.00",rows.getBigDecimal(2).setScale(2).toPlainString());
                            return;
                        }
                    }
                }
                Thread.sleep(250);
            }
            throw new AssertionError("A did not persist the signed MQ fact and result event within 20 seconds");
        }
    }
}
