package cn.bugstack.api.v2;

public class TeamDiscoveryItem implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String teamId;
    private String activityId;
    private String goodsId;
    private String teamVersion;
    private String teamStatus;
    private Integer targetCount;
    private Integer reservedCount;
    private Integer paidCount;
    private String validStartTime;
    private String validEndTime;
    public TeamDiscoveryItem() {}
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public String getTeamVersion() { return teamVersion; }
    public void setTeamVersion(String value) { this.teamVersion = value; }
    public String getTeamStatus() { return teamStatus; }
    public void setTeamStatus(String value) { this.teamStatus = value; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer value) { this.targetCount = value; }
    public Integer getReservedCount() { return reservedCount; }
    public void setReservedCount(Integer value) { this.reservedCount = value; }
    public Integer getPaidCount() { return paidCount; }
    public void setPaidCount(Integer value) { this.paidCount = value; }
    public String getValidStartTime() { return validStartTime; }
    public void setValidStartTime(String value) { this.validStartTime = value; }
    public String getValidEndTime() { return validEndTime; }
    public void setValidEndTime(String value) { this.validEndTime = value; }
}
