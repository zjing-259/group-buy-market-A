package cn.bugstack.api.v2;

public class ReconciliationDecision implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String outTradeNo;
    private String disposition;
    private String paymentId;
    private String closeId;
    private String paidAt;
    private String paidAmount;
    private String currency;
    private String refundId;
    public ReconciliationDecision() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String value) { this.disposition = value; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { this.paymentId = value; }
    public String getCloseId() { return closeId; }
    public void setCloseId(String value) { this.closeId = value; }
    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String value) { this.paidAt = value; }
    public String getPaidAmount() { return paidAmount; }
    public void setPaidAmount(String value) { this.paidAmount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public String getRefundId() { return refundId; }
    public void setRefundId(String value) { this.refundId = value; }
}
