package cn.bugstack.api.v2;

public class TradeQueryRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String outTradeNo;
    public TradeQueryRequest() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
}
