package cn.bugstack.api.v2;

public class Team implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String teamId;
    private String activityId;
    private String goodsId;
    private String source;
    private String channel;
    private Integer targetCount;
    private Integer reservedCount;
    private Integer paidCount;
    private String teamStatus;
    private String teamVersion;
    private String validStartTime;
    private String validEndTime;
    private Boolean joinable;
    private String serverTime;
    private Reservation ownReservation;
    public Team() {}
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public String getSource() { return source; }
    public void setSource(String value) { this.source = value; }
    public String getChannel() { return channel; }
    public void setChannel(String value) { this.channel = value; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer value) { this.targetCount = value; }
    public Integer getReservedCount() { return reservedCount; }
    public void setReservedCount(Integer value) { this.reservedCount = value; }
    public Integer getPaidCount() { return paidCount; }
    public void setPaidCount(Integer value) { this.paidCount = value; }
    public String getTeamStatus() { return teamStatus; }
    public void setTeamStatus(String value) { this.teamStatus = value; }
    public String getTeamVersion() { return teamVersion; }
    public void setTeamVersion(String value) { this.teamVersion = value; }
    public String getValidStartTime() { return validStartTime; }
    public void setValidStartTime(String value) { this.validStartTime = value; }
    public String getValidEndTime() { return validEndTime; }
    public void setValidEndTime(String value) { this.validEndTime = value; }
    public Boolean getJoinable() { return joinable; }
    public void setJoinable(Boolean value) { this.joinable = value; }
    public String getServerTime() { return serverTime; }
    public void setServerTime(String value) { this.serverTime = value; }
    public Reservation getOwnReservation() { return ownReservation; }
    public void setOwnReservation(Reservation value) { this.ownReservation = value; }
}
