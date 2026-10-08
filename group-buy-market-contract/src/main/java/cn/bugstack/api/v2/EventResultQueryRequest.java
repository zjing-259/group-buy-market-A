package cn.bugstack.api.v2;

public class EventResultQueryRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String eventId;
    public EventResultQueryRequest() {}
    public String getEventId() { return eventId; }
    public void setEventId(String value) { this.eventId = value; }
}
