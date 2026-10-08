package cn.bugstack.api.v2;

public class ReserveRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String outTradeNo;
    private String goodsId;
    private String activityId;
    private Integer quantity;
    private String productVersion;
    private String expectedPayPrice;
    private String currency;
    private String teamId;
    public ReserveRequest() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String value) { this.outTradeNo = value; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public String getProductVersion() { return productVersion; }
    public void setProductVersion(String value) { this.productVersion = value; }
    public String getExpectedPayPrice() { return expectedPayPrice; }
    public void setExpectedPayPrice(String value) { this.expectedPayPrice = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
}
