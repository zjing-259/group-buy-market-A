package cn.bugstack.v2;

import cn.bugstack.api.v2.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.UUID;

@Service
public class V2OutboxService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ObjectMapper json;
    @Value("${ab.v2.env:dev}") private String environment;

    public String enqueue(String baseTopic, String eventType, String source, String channel,
                          String correlationId, Object payload) {
        EventEnvelope event = new EventEnvelope();
        event.setEventId(UUID.randomUUID().toString()); event.setEventType(eventType);
        event.setSchemaVersion(1); event.setProducer("group-buy-market");
        event.setOccurredAt(V2Format.now()); event.setSource(source); event.setChannel(channel);
        event.setCorrelationId(correlationId); event.setPayload(payload);
        try {
            String body = json.writeValueAsString(event);
            if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256 * 1024)
                throw new V2Failure("EVENT_TOO_LARGE");
            jdbc.update("insert into ab_outbox(event_id,topic,event_type,source,channel,body,body_hash) " +
                            "values(?,?,?,?,?,?,?)",event.getEventId(),baseTopic+"-"+environment,eventType,
                    source,channel,body,V2Format.sha256(body));
            return event.getEventId();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
