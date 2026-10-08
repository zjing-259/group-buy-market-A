package cn.bugstack.api.v2;

public class CloseResponse implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String outTradeNo;
    private String orderId;
    private String teamId;
    private String orderStatus;
    private String closedAt;
    private String recordVersion;
    private String teamVersion;
    public CloseResponse() {}
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { this.orderId = value; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String value) { this.orderStatus = value; }
    public String getClosedAt() { return closedAt; }
    public void setClosedAt(String value) { this.closedAt = value; }
    public String getRecordVersion() { return recordVersion; }
    public void setRecordVersion(String value) { this.recordVersion = value; }
    public String getTeamVersion() { return teamVersion; }
    public void setTeamVersion(String value) { this.teamVersion = value; }
}
