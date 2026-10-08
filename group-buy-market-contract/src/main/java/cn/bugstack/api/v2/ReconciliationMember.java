package cn.bugstack.api.v2;

public class ReconciliationMember implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String outTradeNo;
    private String orderId;
    private String orderStatus;
    private String reservedAt;
    private String paymentDeadline;
    private String payPrice;
    private String currency;
    private Payment payment;
    public ReconciliationMember() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { this.orderId = value; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String value) { this.orderStatus = value; }
    public String getReservedAt() { return reservedAt; }
    public void setReservedAt(String value) { this.reservedAt = value; }
    public String getPaymentDeadline() { return paymentDeadline; }
    public void setPaymentDeadline(String value) { this.paymentDeadline = value; }
    public String getPayPrice() { return payPrice; }
    public void setPayPrice(String value) { this.payPrice = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment value) { this.payment = value; }
}
