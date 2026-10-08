package cn.bugstack.api.v2;

public class ReconciliationCompleteRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String teamId;
    private String reconciliationId;
    private String rosterVersion;
    private String completionId;
    private String checkedAt;
    private java.util.List<ReconciliationDecision> results;
    public ReconciliationCompleteRequest() {}
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(String value) { this.reconciliationId = value; }
    public String getRosterVersion() { return rosterVersion; }
    public void setRosterVersion(String value) { this.rosterVersion = value; }
    public String getCompletionId() { return completionId; }
    public void setCompletionId(String value) { this.completionId = value; }
    public String getCheckedAt() { return checkedAt; }
    public void setCheckedAt(String value) { this.checkedAt = value; }
    public java.util.List<ReconciliationDecision> getResults() { return results; }
    public void setResults(java.util.List<ReconciliationDecision> value) { this.results = value; }
}
