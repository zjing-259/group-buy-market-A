package cn.bugstack.api.v2;

public class EventEnvelope implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String eventId;
    private String eventType;
    private Integer schemaVersion;
    private String producer;
    private String occurredAt;
    private String source;
    private String channel;
    private String correlationId;
    private Object payload;
    public EventEnvelope() {}
    public String getEventId() { return eventId; }
    public void setEventId(String value) { this.eventId = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { this.eventType = value; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer value) { this.schemaVersion = value; }
    public String getProducer() { return producer; }
    public void setProducer(String value) { this.producer = value; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String value) { this.occurredAt = value; }
    public String getSource() { return source; }
    public void setSource(String value) { this.source = value; }
    public String getChannel() { return channel; }
    public void setChannel(String value) { this.channel = value; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String value) { this.correlationId = value; }
    public Object getPayload() { return payload; }
    public void setPayload(Object value) { this.payload = value; }
}
