package cn.bugstack.api.v2;

public class TeamCompleted implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String teamId;
    private String activityId;
    private String teamVersion;
    private java.util.List<String> outTradeNoList;
    public TeamCompleted() {}
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public String getTeamVersion() { return teamVersion; }
    public void setTeamVersion(String value) { this.teamVersion = value; }
    public java.util.List<String> getOutTradeNoList() { return outTradeNoList; }
    public void setOutTradeNoList(java.util.List<String> value) { this.outTradeNoList = value; }
}
