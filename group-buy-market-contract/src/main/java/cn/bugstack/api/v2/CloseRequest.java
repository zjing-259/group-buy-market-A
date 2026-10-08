package cn.bugstack.api.v2;

public class CloseRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String outTradeNo;
    private String closeId;
    private String reason;
    private String paymentDisposition;
    private String verifiedAt;
    public CloseRequest() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
    public String getCloseId() { return closeId; }
    public void setCloseId(String value) { this.closeId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public String getPaymentDisposition() { return paymentDisposition; }
    public void setPaymentDisposition(String value) { this.paymentDisposition = value; }
    public String getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(String value) { this.verifiedAt = value; }
}
