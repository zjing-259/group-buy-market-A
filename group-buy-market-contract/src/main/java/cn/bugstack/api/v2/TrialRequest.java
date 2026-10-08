package cn.bugstack.api.v2;

public class TrialRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String goodsId;
    private Integer quantity;
    private String productVersion;
    private String activityId;
    public TrialRequest() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public String getProductVersion() { return productVersion; }
    public void setProductVersion(String value) { this.productVersion = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
}
