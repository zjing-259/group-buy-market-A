package cn.bugstack.v2;

import cn.bugstack.api.v2.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2MqTransport {
    private static final Logger log=LoggerFactory.getLogger(V2MqTransport.class);
    @Resource private V2EventEngine events;
    @Resource private ObjectMapper json;
    @Value("${ab.v2.env:dev}") private String env;
    @Value("${ab.v2.rocketmq.nameserver:127.0.0.1:9876}") private String nameserver;
    @Value("${ab.v2.rocketmq.access-key:}") private String accessKey;
    @Value("${ab.v2.rocketmq.secret-key:}") private String secretKey;
    @Value("${ab.v2.rocketmq.use-tls:false}") private boolean useTls;
    @Value("${ab.v2.rocketmq.require-tls:true}") private boolean requireTls;
    @Value("${ab.v2.mq.a-key-id:}") private String aKeyId;
    @Value("${ab.v2.mq.a-secret:}") private String aSecret;
    @Value("${ab.v2.mq.b-key-id:}") private String bKeyId;
    @Value("${ab.v2.mq.b-secret:}") private String bSecret;
    @Value("${ab.v2.mq.b-verification-keys:{}}") private String bVerificationKeys;
    @Value("${ab.v2.rpc.client-id:}") private String bClientId;
    @Value("${ab.v2.rpc.source:}") private String source;
    @Value("${ab.v2.rpc.channel:}") private String channel;
    private volatile DefaultMQProducer producer;
    private volatile DefaultMQPushConsumer productConsumer;
    private volatile DefaultMQPushConsumer tradeConsumer;

    @Scheduled(initialDelay=1000,fixedDelay=10000)
    public synchronized void ensureClients() {
        if (requireTls && !useTls) {
            log.error("v2 MQ requires TLS; transport remains disabled and outbox remains durable"); return;
        }
        if (accessKey.isEmpty() || secretKey.isEmpty() || aSecret.isEmpty() || bSecret.isEmpty() ||
                aKeyId.isEmpty() || bKeyId.isEmpty() || bClientId.isEmpty() || source.isEmpty() || channel.isEmpty()) {
            log.error("v2 MQ credentials are incomplete; transport remains disabled"); return;
        }
        if (producer==null) {
            try {
                DefaultMQProducer next=new DefaultMQProducer("A_V2_EVENTS_"+env,
                        new AclClientRPCHook(new SessionCredentials(accessKey,secretKey)));
                next.setNamesrvAddr(nameserver); next.setUseTLS(useTls); next.setRetryTimesWhenSendFailed(0); next.setSendMsgTimeout(5000);
                next.start(); producer=next;
            } catch (Exception e) { log.warn("v2 MQ producer unavailable; outbox remains durable",e); }
        }
        if (productConsumer==null) productConsumer=startConsumer("A_PRODUCT_SYNC-"+env,"B_PRODUCT_EVENTS-"+env,"PRODUCT_SNAPSHOT_CHANGED");
        if (tradeConsumer==null) tradeConsumer=startConsumer("A_TRADE_FACTS-"+env,"B_TRADE_EVENTS-"+env,
                "PAYMENT_SUCCEEDED || REFUND_STATUS_CHANGED");
    }

    private DefaultMQPushConsumer startConsumer(String group,String topic,String tags) {
        DefaultMQPushConsumer c=new DefaultMQPushConsumer(group,
                new AclClientRPCHook(new SessionCredentials(accessKey,secretKey)),
                new AllocateMessageQueueAveragely());
        try {
            c.setNamesrvAddr(nameserver); c.setUseTLS(useTls); c.setMaxReconsumeTimes(8); c.setConsumeThreadMin(2); c.setConsumeThreadMax(8);
            c.subscribe(topic,tags);
            c.registerMessageListener((MessageListenerConcurrently)(messages,context)->{
                for (MessageExt message:messages) {
                    try { receive(message); }
                    catch (Exception failure) {
                        log.error("v2 MQ consume failed topic:{} messageId:{}",message.getTopic(),message.getMsgId(),failure);
                        // Broker delay levels 3/4/5/6 are explicitly configured as 1/2/4/8 minutes.
                        context.setDelayLevelWhenNextConsume(Math.min(6,3+message.getReconsumeTimes()));
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            c.start(); return c;
        } catch (Exception e) {
            log.warn("v2 MQ consumer unavailable group:{}",group,e);
            try { c.shutdown(); } catch (Exception ignored) { }
            return null;
        }
    }

    private void receive(MessageExt message) throws Exception {
        byte[] body=message.getBody();
        if (body==null || body.length>256*1024) {
            events.quarantine(null,"INVALID_SIZE",""); return;
        }
        String raw;
        try { raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString(); }
        catch (CharacterCodingException bad) {
            events.quarantine(null,"INVALID_UTF8",Base64.getEncoder().encodeToString(body)); return;
        }
        String verificationSecret=V2VerificationKeys.resolve(bKeyId,bSecret,bVerificationKeys,message.getProperty("keyId"));
        if (verificationSecret==null) { events.quarantine(null,"UNKNOWN_MQ_KEY",raw); return; }
        String expected=V2Security.hmac(verificationSecret,message.getTopic()+"\n"+V2Format.sha256(body));
        String supplied=message.getProperty("signature");
        if (supplied==null ||
                !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII))) {
            events.quarantine(null,"INVALID_MQ_SIGNATURE",raw); return;
        }
        EventEnvelope event;
        try { event=json.readValue(raw,EventEnvelope.class); }
        catch (Exception bad) { events.quarantine(null,"INVALID_JSON",raw); return; }
        if (!Objects.equals(message.getKeys(),event.getEventId()) ||
                !Objects.equals(message.getTags(),event.getEventType()) ||
                !Objects.equals(message.getProperty("source"),event.getSource()) ||
                !Objects.equals(message.getProperty("channel"),event.getChannel()) ||
                !bClientId.equals(event.getProducer()) || !source.equals(event.getSource()) ||
                !channel.equals(event.getChannel()) || !authorizedTopic(message.getTopic(),event.getEventType())) {
            events.quarantine(event.getEventId(),"MQ_PROPERTY_MISMATCH",raw); return;
        }
        events.process(raw,bClientId);
    }

    private boolean authorizedTopic(String topic,String type) {
        return ("B_PRODUCT_EVENTS-"+env).equals(topic) && "PRODUCT_SNAPSHOT_CHANGED".equals(type) ||
                ("B_TRADE_EVENTS-"+env).equals(topic) &&
                        ("PAYMENT_SUCCEEDED".equals(type) || "REFUND_STATUS_CHANGED".equals(type));
    }

    public void send(V2OutboxStore.Claim claim) throws Exception {
        DefaultMQProducer sender=producer;
        if (sender==null) throw new IllegalStateException("MQ_PRODUCER_UNAVAILABLE");
        byte[] body=claim.body.getBytes(StandardCharsets.UTF_8);
        Message message=new Message(claim.topic,claim.eventType,claim.eventId,body);
        message.putUserProperty("keyId",aKeyId);
        message.putUserProperty("signature",V2Security.hmac(aSecret,claim.topic+"\n"+V2Format.sha256(body)));
        message.putUserProperty("source",claim.source); message.putUserProperty("channel",claim.channel);
        SendResult result=sender.send(message,5000);
        if (result==null || result.getSendStatus()!=SendStatus.SEND_OK) throw new IllegalStateException("MQ_SEND_NOT_CONFIRMED");
    }

    @PreDestroy public synchronized void close() {
        if (productConsumer!=null) productConsumer.shutdown();
        if (tradeConsumer!=null) tradeConsumer.shutdown();
        if (producer!=null) producer.shutdown();
    }
}
