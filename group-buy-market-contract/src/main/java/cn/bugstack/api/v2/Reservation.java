package cn.bugstack.api.v2;

public class Reservation implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String recordKind;
    private String orderId;
    private String outTradeNo;
    private String userId;
    private String source;
    private String channel;
    private String activityId;
    private String goodsId;
    private String productVersion;
    private Integer quantity;
    private String teamId;
    private String originalPrice;
    private String deductionPrice;
    private String payPrice;
    private String currency;
    private String reservedAt;
    private String paymentDeadline;
    private String orderStatus;
    private String teamStatus;
    private String recordVersion;
    private String teamVersion;
    private String serverTime;
    private String closedAt;
    private Payment payment;
    private Refund refund;
    public Reservation() {}
    public String getRecordKind() { return recordKind; }
    public void setRecordKind(String value) { this.recordKind = value; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { this.orderId = value; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getSource() { return source; }
    public void setSource(String value) { this.source = value; }
    public String getChannel() { return channel; }
    public void setChannel(String value) { this.channel = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public String getProductVersion() { return productVersion; }
    public void setProductVersion(String value) { this.productVersion = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(String value) { this.originalPrice = value; }
    public String getDeductionPrice() { return deductionPrice; }
    public void setDeductionPrice(String value) { this.deductionPrice = value; }
    public String getPayPrice() { return payPrice; }
    public void setPayPrice(String value) { this.payPrice = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public String getReservedAt() { return reservedAt; }
    public void setReservedAt(String value) { this.reservedAt = value; }
    public String getPaymentDeadline() { return paymentDeadline; }
    public void setPaymentDeadline(String value) { this.paymentDeadline = value; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String value) { this.orderStatus = value; }
    public String getTeamStatus() { return teamStatus; }
    public void setTeamStatus(String value) { this.teamStatus = value; }
    public String getRecordVersion() { return recordVersion; }
    public void setRecordVersion(String value) { this.recordVersion = value; }
    public String getTeamVersion() { return teamVersion; }
    public void setTeamVersion(String value) { this.teamVersion = value; }
    public String getServerTime() { return serverTime; }
    public void setServerTime(String value) { this.serverTime = value; }
    public String getClosedAt() { return closedAt; }
    public void setClosedAt(String value) { this.closedAt = value; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment value) { this.payment = value; }
    public Refund getRefund() { return refund; }
    public void setRefund(Refund value) { this.refund = value; }
}
