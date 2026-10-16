package cn.bugstack.api.v2;

public class TeamDiscoveryPage implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String source;
    private String channel;
    private String userId;
    private String goodsId;
    private String activityId;
    private String productVersion;
    private String serverTime;
    private java.util.List<TeamDiscoveryItem> items;
    private String nextCursor;
    private Boolean hasMore;
    public TeamDiscoveryPage() {}
    public String getSource() { return source; }
    public void setSource(String value) { this.source = value; }
    public String getChannel() { return channel; }
    public void setChannel(String value) { this.channel = value; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public String getProductVersion() { return productVersion; }
    public void setProductVersion(String value) { this.productVersion = value; }
    public String getServerTime() { return serverTime; }
    public void setServerTime(String value) { this.serverTime = value; }
    public java.util.List<TeamDiscoveryItem> getItems() { return items; }
    public void setItems(java.util.List<TeamDiscoveryItem> value) { this.items = value; }
    public String getNextCursor() { return nextCursor; }
    public void setNextCursor(String value) { this.nextCursor = value; }
    public Boolean getHasMore() { return hasMore; }
    public void setHasMore(Boolean value) { this.hasMore = value; }
}
