package cn.bugstack.api.v2;

public class TeamDiscoveryRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String goodsId;
    private String activityId;
    private String productVersion;
    private Integer limit;
    private String cursor;
    public TeamDiscoveryRequest() {}
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public String getProductVersion() { return productVersion; }
    public void setProductVersion(String value) { this.productVersion = value; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer value) { this.limit = value; }
    public String getCursor() { return cursor; }
    public void setCursor(String value) { this.cursor = value; }
}
