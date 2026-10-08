package cn.bugstack.api.v2;

public class EventProcessingResultPayload implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String originalEventId;
    private String originalEventType;
    private String outcome;
    private String code;
    private String info;
    private String processedAt;
    private Object result;
    public EventProcessingResultPayload() {}
    public String getOriginalEventId() { return originalEventId; }
    public void setOriginalEventId(String value) { this.originalEventId = value; }
    public String getOriginalEventType() { return originalEventType; }
    public void setOriginalEventType(String value) { this.originalEventType = value; }
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
