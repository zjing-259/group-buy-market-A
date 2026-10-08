package cn.bugstack.api.v2;

public class RefundStatusChanged implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String outTradeNo;
    private String paymentId;
    private String refundId;
    private String refundAmount;
    private String currency;
    private String refundStatus;
    private String refundVersion;
    private String occurredAt;
    private String failureCode;
    private String paidAt;
    private String paidAmount;
    public RefundStatusChanged() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { this.paymentId = value; }
    public String getRefundId() { return refundId; }
    public void setRefundId(String value) { this.refundId = value; }
    public String getRefundAmount() { return refundAmount; }
    public void setRefundAmount(String value) { this.refundAmount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String value) { this.refundStatus = value; }
    public String getRefundVersion() { return refundVersion; }
    public void setRefundVersion(String value) { this.refundVersion = value; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String value) { this.occurredAt = value; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String value) { this.failureCode = value; }
    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String value) { this.paidAt = value; }
    public String getPaidAmount() { return paidAmount; }
    public void setPaidAmount(String value) { this.paidAmount = value; }
}
