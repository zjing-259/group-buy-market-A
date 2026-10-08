package cn.bugstack.api.v2;

public class TeamFailed implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String teamId;
    private String activityId;
    private String teamVersion;
    private java.util.List<String> outTradeNoList;
    private String reconciliationId;
    private String reason;
    private java.util.List<String> refundRequiredOutTradeNoList;
    public TeamFailed() {}
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public String getTeamVersion() { return teamVersion; }
    public void setTeamVersion(String value) { this.teamVersion = value; }
    public java.util.List<String> getOutTradeNoList() { return outTradeNoList; }
    public void setOutTradeNoList(java.util.List<String> value) { this.outTradeNoList = value; }
    public String getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(String value) { this.reconciliationId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public java.util.List<String> getRefundRequiredOutTradeNoList() { return refundRequiredOutTradeNoList; }
    public void setRefundRequiredOutTradeNoList(java.util.List<String> value) { this.refundRequiredOutTradeNoList = value; }
}
