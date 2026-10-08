package cn.bugstack.api.v2;

public class ReconciliationQueryRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String teamId;
    public ReconciliationQueryRequest() {}
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
}
