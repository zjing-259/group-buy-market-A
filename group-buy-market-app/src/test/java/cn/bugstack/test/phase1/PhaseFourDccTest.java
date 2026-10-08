package cn.bugstack.test.phase1;

import cn.bugstack.config.DCCValueBeanFactory;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.trigger.http.DCCController;
import cn.bugstack.types.enums.ResponseCode;
import org.junit.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PhaseFourDccTest {
    @Test public void invalidPublishDoesNotReachSubscribers() {
        DCCController controller = new DCCController();
        RTopic topic = mock(RTopic.class);
        ReflectionTestUtils.setField(controller, "dccTopic", topic);
        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), controller.updateConfig("cutRange", "101").getCode());
        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), controller.updateConfig("cutRange", "-1").getCode());
        verify(topic, never()).publish(anyString());
        assertEquals(ResponseCode.SUCCESS.getCode(), controller.updateConfig("cutRange", "10").getCode());
        verify(topic).publish("cutRange,10");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test public void invalidSubscriberMessagePreservesLastValidPercentage() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RBucket bucket = mock(RBucket.class);
        when(client.getTopic("group_buy_market_dcc")).thenReturn(topic);
        when(client.getBucket(anyString())).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn("10");
        DCCValueBeanFactory factory = new DCCValueBeanFactory(client);
        DCCService service = new DCCService();
        factory.postProcessAfterInitialization(service, "dccService");
        assertEquals("10", ReflectionTestUtils.getField(service, "cutRange"));
        factory.dccRedisTopicListener();
        org.mockito.ArgumentCaptor<MessageListener> captor = org.mockito.ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(String.class), captor.capture());
        MessageListener listener = captor.getValue();
        listener.onMessage("group_buy_market_dcc", "cutRange,101");
        assertEquals("10", ReflectionTestUtils.getField(service, "cutRange"));
        verify(bucket, never()).set("101");
        listener.onMessage("group_buy_market_dcc", "cutRange,20");
        assertEquals("20", ReflectionTestUtils.getField(service, "cutRange"));
        verify(bucket).set("20");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test public void invalidStoredPercentageFallsBackToDefault() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket bucket = mock(RBucket.class);
        when(client.getBucket(anyString())).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn("101");
        DCCService service = new DCCService();
        new DCCValueBeanFactory(client).postProcessAfterInitialization(service, "dccService");
        assertEquals("100", ReflectionTestUtils.getField(service, "cutRange"));
        verify(bucket).set("100");
    }
}
