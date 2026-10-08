package cn.bugstack.api.v2;

public class PaymentSucceeded implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String outTradeNo;
    private String paymentId;
    private String paidAmount;
    private String currency;
    private String paidAt;
    public PaymentSucceeded() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { this.paymentId = value; }
    public String getPaidAmount() { return paidAmount; }
    public void setPaidAmount(String value) { this.paidAmount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String value) { this.paidAt = value; }
}
