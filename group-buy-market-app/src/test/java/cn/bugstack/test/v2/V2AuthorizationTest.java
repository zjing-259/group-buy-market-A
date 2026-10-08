package cn.bugstack.test.v2;

import cn.bugstack.api.v2.*;
import cn.bugstack.v2.*;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class V2AuthorizationTest {
    private V2Security security;
    private RedissonClient redis;
    private RBucket<Object> bucket;
    private Invoker<TradeService> invoker;
    private Invocation invocation;
    private TradeQueryRequest request;

    @Before @SuppressWarnings("unchecked") public void setup() {
        security=new V2Security(); redis=mock(RedissonClient.class); bucket=mock(RBucket.class);
        ReflectionTestUtils.setField(security,"redis",redis);
        for (String[] field:new String[][]{{"clientId","b-test"},{"keyId","current"},{"secret","current-test-secret"},
                {"source","s01"},{"channel","c01"},{"verificationKeys","{\"retained\":\"previous-test-secret\"}"}})
            ReflectionTestUtils.setField(security,field[0],field[1]);
        ReflectionTestUtils.setField(security,"requireTls",false);
        when(redis.<Object>getBucket(anyString())).thenReturn(bucket);
        when(bucket.trySet(any(),eq(660L),eq(TimeUnit.SECONDS))).thenReturn(true);
        invoker=mock(Invoker.class); invocation=mock(Invocation.class);
        when(invoker.getInterface()).thenReturn(TradeService.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("tri://127.0.0.1:50051?version=2.0.0&group=gbm-test"));
        when(invocation.getMethodName()).thenReturn("query");
        request=new TradeQueryRequest(); request.setSource("s01"); request.setChannel("c01");
        request.setUserId("owner"); request.setOutTradeNo("order");
        when(invocation.getArguments()).thenReturn(new Object[]{request});
    }

    private void sign(String keyId,String secret,long timestamp) {
        String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        RpcContext.getServerAttachment().setAttachment("clientId","b-test");
        RpcContext.getServerAttachment().setAttachment("keyId",keyId);
        RpcContext.getServerAttachment().setAttachment("timestamp",Long.toString(timestamp));
        RpcContext.getServerAttachment().setAttachment("nonce",nonce);
        RpcContext.getServerAttachment().setAttachment("requestId","request-test");
        String text=String.join("\n","b-test",keyId,Long.toString(timestamp),nonce,"request-test",
                TradeService.class.getName(),"query","2.0.0","gbm-test",V2Format.sha256(security.canonicalJson(request)));
        RpcContext.getServerAttachment().setAttachment("signature",V2Security.hmac(secret,text));
    }

    private void rejected(String code) {
        try {security.verify(invoker,invocation); fail("request should fail: "+code);}
        catch (V2AuthenticationException expected) {assertEquals(code,expected.getCode());}
    }

    @Test public void activeAndRetainedKeysVerifyDuringRotation() {
        sign("current","current-test-secret",System.currentTimeMillis()); security.verify(invoker,invocation);
        sign("retained","previous-test-secret",System.currentTimeMillis()); security.verify(invoker,invocation);
        verify(bucket,times(2)).trySet(any(),eq(660L),eq(TimeUnit.SECONDS));
    }

    @Test public void wrongSignatureAndExpiredTimestampDoNotConsumeNonce() {
        sign("current","incorrect-secret",System.currentTimeMillis()); rejected("AUTH_INVALID");
        sign("current","current-test-secret",System.currentTimeMillis()-301000); rejected("AUTH_INVALID");
        sign("unregistered","test",System.currentTimeMillis()); rejected("AUTH_INVALID");
        verifyNoInteractions(redis);
    }

    @Test public void signedForeignChannelCannotAccessOrders() {
        request.setChannel("c02"); sign("current","current-test-secret",System.currentTimeMillis());
        rejected("FORBIDDEN"); verifyNoInteractions(redis);
    }

    @Test public void replayAndUnavailableNonceStoreFailClosed() {
        sign("current","current-test-secret",System.currentTimeMillis());
        when(bucket.trySet(any(),eq(660L),eq(TimeUnit.SECONDS))).thenReturn(false); rejected("REPLAY_REJECTED");
        when(bucket.trySet(any(),eq(660L),eq(TimeUnit.SECONDS))).thenThrow(new IllegalStateException("redis unavailable"));
        rejected("DEPENDENCY_UNAVAILABLE");
    }

    @Test public void tlsIsRequiredUnlessExplicitlyDisabled() {
        ReflectionTestUtils.setField(security,"requireTls",true);
        sign("current","current-test-secret",System.currentTimeMillis()); rejected("AUTH_REQUIRED");
        verifyNoInteractions(redis);
    }

    private V2MqTransport transport(V2EventEngine events) {
        V2MqTransport transport=new V2MqTransport(); ReflectionTestUtils.setField(transport,"events",events);
        ReflectionTestUtils.setField(transport,"json",new com.fasterxml.jackson.databind.ObjectMapper());
        for (String[] field:new String[][]{{"env","test"},{"bKeyId","current"},{"bSecret","current-test-secret"},
                {"bVerificationKeys","{\"retained\":\"previous-test-secret\"}"},{"bClientId","b-test"},
                {"source","s01"},{"channel","c01"}}) ReflectionTestUtils.setField(transport,field[0],field[1]);
        return transport;
    }

    private MessageExt message(String topic,String type,String source,String keyId,String key) {
        String raw="{\"eventId\":\"event-test\",\"eventType\":\""+type+"\",\"schemaVersion\":1,"+
                "\"producer\":\"b-test\",\"occurredAt\":\"2026-10-02T00:00:00.000Z\",\"source\":\""+source+"\","+
                "\"channel\":\"c01\",\"payload\":{}}";
        MessageExt m=new MessageExt(); m.setTopic(topic); m.setTags(type); m.setKeys("event-test");
        m.setBody(raw.getBytes(StandardCharsets.UTF_8)); m.putUserProperty("source",source); m.putUserProperty("channel","c01");
        m.putUserProperty("keyId",keyId); m.putUserProperty("signature",V2Security.hmac(key,topic+"\n"+V2Format.sha256(m.getBody())));
        return m;
    }

    @Test public void mqRetainedKeyAcceptsOldMessageButRejectsForeignScopeAndTopic() {
        V2EventEngine events=mock(V2EventEngine.class); V2MqTransport t=transport(events);
        MessageExt good=message("B_PRODUCT_EVENTS-test","PRODUCT_SNAPSHOT_CHANGED","s01","retained","previous-test-secret");
        ReflectionTestUtils.invokeMethod(t,"receive",good);
        verify(events).process(new String(good.getBody(),StandardCharsets.UTF_8),"b-test");
        ReflectionTestUtils.invokeMethod(t,"receive",message("B_PRODUCT_EVENTS-test","PRODUCT_SNAPSHOT_CHANGED","s02","current","current-test-secret"));
        ReflectionTestUtils.invokeMethod(t,"receive",message("B_PRODUCT_EVENTS-test","PAYMENT_SUCCEEDED","s01","current","current-test-secret"));
        verify(events,times(2)).quarantine(eq("event-test"),eq("MQ_PROPERTY_MISMATCH"),anyString());
        verify(events,times(1)).process(anyString(),anyString());
    }

    @Test public void unknownMqKeyIsRecoverableQuarantineAndPersistenceFailurePropagates() {
        V2EventEngine events=mock(V2EventEngine.class); V2MqTransport t=transport(events);
        MessageExt unknown=message("B_PRODUCT_EVENTS-test","PRODUCT_SNAPSHOT_CHANGED","s01","unknown","unknown-secret");
        ReflectionTestUtils.invokeMethod(t,"receive",unknown);
        verify(events).quarantine(isNull(),eq("UNKNOWN_MQ_KEY"),anyString());
        doThrow(new IllegalStateException("database unavailable")).when(events).quarantine(isNull(),eq("UNKNOWN_MQ_KEY"),anyString());
        try {ReflectionTestUtils.invokeMethod(t,"receive",unknown); fail("quarantine failure must cause MQ retry");}
        catch (IllegalStateException expected) {assertEquals("database unavailable",expected.getMessage());}
        verify(events,never()).process(anyString(),anyString());
    }
}
