package cn.bugstack.api.v2;

public class EventProcessingResult implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String eventId;
    private String eventType;
    private String processingStatus;
    private String outcome;
    private String code;
    private String info;
    private String processedAt;
    private Object result;
    public EventProcessingResult() {}
    public String getEventId() { return eventId; }
    public void setEventId(String value) { this.eventId = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { this.eventType = value; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String value) { this.processingStatus = value; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String value) { this.outcome = value; }
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }
    public String getInfo() { return info; }
    public void setInfo(String value) { this.info = value; }
    public String getProcessedAt() { return processedAt; }
    public void setProcessedAt(String value) { this.processedAt = value; }
    public Object getResult() { return result; }
    public void setResult(Object value) { this.result = value; }
}
