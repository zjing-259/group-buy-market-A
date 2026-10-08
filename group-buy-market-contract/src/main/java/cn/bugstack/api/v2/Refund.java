package cn.bugstack.api.v2;

public class Refund implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String refundId;
    private String refundStatus;
    private String refundVersion;
    private String refundAmount;
    private String currency;
    public Refund() {}
    public String getRefundId() { return refundId; }
    public void setRefundId(String value) { this.refundId = value; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String value) { this.refundStatus = value; }
    public String getRefundVersion() { return refundVersion; }
    public void setRefundVersion(String value) { this.refundVersion = value; }
    public String getRefundAmount() { return refundAmount; }
    public void setRefundAmount(String value) { this.refundAmount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
}
