package cn.bugstack.api.v2;

public class ReconciliationResult implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String teamId;
    private String reconciliationId;
    private String teamStatus;
    private String teamVersion;
    private String resolvedAt;
    public ReconciliationResult() {}
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(String value) { this.reconciliationId = value; }
    public String getTeamStatus() { return teamStatus; }
    public void setTeamStatus(String value) { this.teamStatus = value; }
    public String getTeamVersion() { return teamVersion; }
    public void setTeamVersion(String value) { this.teamVersion = value; }
    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String value) { this.resolvedAt = value; }
}
