package cn.bugstack.compatibility;

import org.apache.rocketmq.acl.common.*;
import org.apache.rocketmq.client.consumer.*;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Accelerated, dedicated development group; does not change the A business group's eight-retry policy. */
public class MqDeadLetterTest {
    @Test void finiteBrokerRetryPreservesOriginalEventInDeadLetterQueue() throws Exception {
        String group="A_V2_RETRY_TEST-dev",id="retry-"+UUID.randomUUID();
        byte[] body=("{\"eventId\":\""+id+"\"}").getBytes(StandardCharsets.UTF_8);
        AclClientRPCHook aHook=new AclClientRPCHook(new SessionCredentials(System.getenv("AB_V2_TEST_A_ACCESS_KEY"),
                System.getenv("AB_V2_TEST_A_SECRET_KEY")));
        DefaultMQPushConsumer consumer=new DefaultMQPushConsumer(group,aHook,new AllocateMessageQueueAveragely());
        consumer.setInstanceName("retry-push"); consumer.setNamesrvAddr("127.0.0.1:9876");
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET); consumer.setMaxReconsumeTimes(1);
        consumer.setConsumeThreadMin(1); consumer.setConsumeThreadMax(1);
        consumer.subscribe("B_PRODUCT_EVENTS-dev","*"); AtomicInteger deliveries=new AtomicInteger();
        consumer.registerMessageListener((MessageListenerConcurrently)(messages,context)->{
            for (MessageExt m:messages) if (id.equals(m.getKeys())) {
                deliveries.incrementAndGet(); context.setDelayLevelWhenNextConsume(1);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        // A separate group prevents the pull inspector's empty subscription from replacing the push subscription.
        DefaultMQPullConsumer reader=new DefaultMQPullConsumer("A_V2_DLQ_TEST-dev",aHook);
        reader.setInstanceName("retry-dlq-reader"); reader.setNamesrvAddr("127.0.0.1:9876");
        DefaultMQProducer producer=new DefaultMQProducer("B_RETRY_TEST_"+UUID.randomUUID(),new AclClientRPCHook(new SessionCredentials(
                System.getenv("AB_V2_ROCKETMQ_ACCESS_KEY"),System.getenv("AB_V2_ROCKETMQ_SECRET_KEY"))));
        producer.setNamesrvAddr("127.0.0.1:9876"); producer.setRetryTimesWhenSendFailed(0);
        try {
            consumer.start(); reader.start(); producer.start();
            producer.send(new Message("B_PRODUCT_EVENTS-dev","PRODUCT_SNAPSHOT_CHANGED",id,body),10000);
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(50);
            while (System.nanoTime()<deadline) {
                try {
                    for (MessageQueue queue:reader.fetchSubscribeMessageQueues("%DLQ%"+group)) {
                        long offset=reader.minOffset(queue),end=reader.maxOffset(queue);
                        while (offset<end) {
                            PullResult result=reader.pull(queue,"*",offset,32); offset=result.getNextBeginOffset();
                            if (result.getMsgFoundList()==null) break;
                            for (MessageExt dead:result.getMsgFoundList()) if (id.equals(dead.getKeys())) {
                                assertArrayEquals(body,dead.getBody()); assertEquals(2,deliveries.get()); return;
                            }
                        }
                    }
                } catch (org.apache.rocketmq.client.exception.MQClientException routeNotReady) {
                    // The dedicated DLQ topic is created only once a retry is exhausted.
                }
                Thread.sleep(250);
            }
            fail("No original event in DLQ within 50 seconds; deliveries="+deliveries.get());
        } finally {producer.shutdown(); reader.shutdown(); consumer.shutdown();}
    }
}
