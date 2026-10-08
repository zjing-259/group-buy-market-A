package cn.bugstack.api.v2;

public class ReconciliationSnapshot implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String teamStatus;
    private String teamVersion;
    private String reconciliationId;
    private String rosterVersion;
    private String validEndTime;
    private java.util.List<ReconciliationMember> members;
    public ReconciliationSnapshot() {}
    public String getTeamStatus() { return teamStatus; }
    public void setTeamStatus(String value) { this.teamStatus = value; }
    public String getTeamVersion() { return teamVersion; }
    public void setTeamVersion(String value) { this.teamVersion = value; }
    public String getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(String value) { this.reconciliationId = value; }
    public String getRosterVersion() { return rosterVersion; }
    public void setRosterVersion(String value) { this.rosterVersion = value; }
    public String getValidEndTime() { return validEndTime; }
    public void setValidEndTime(String value) { this.validEndTime = value; }
    public java.util.List<ReconciliationMember> getMembers() { return members; }
    public void setMembers(java.util.List<ReconciliationMember> value) { this.members = value; }
}
