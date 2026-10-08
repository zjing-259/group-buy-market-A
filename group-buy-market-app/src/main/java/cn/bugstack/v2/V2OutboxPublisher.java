package cn.bugstack.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

@Component
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2OutboxPublisher {
    private static final Logger log=LoggerFactory.getLogger(V2OutboxPublisher.class);
    @Resource private V2OutboxStore store;
    @Resource private V2MqTransport transport;

    @Scheduled(initialDelay=5000,fixedDelay=3000)
    public void publishDue() {
        for (String id:store.dueIds()) {
            V2OutboxStore.Claim claim=null;
            try {
                claim=store.claim(id);
                if (claim==null) continue;
                transport.send(claim);
                store.published(claim);
            } catch (Exception failure) {
                log.warn("v2 outbox publish failed eventId:{}",id,failure);
                if (claim!=null) {
                    try {
                        store.failed(claim,failure.getClass().getSimpleName());
                        if (claim.attempts>=5) log.error("v2 outbox requires attention eventId:{} attempts:{}",id,claim.attempts);
                    } catch (Exception persistFailure) { log.error("v2 outbox result update failed eventId:{}",id,persistFailure); }
                }
            }
        }
    }
}
