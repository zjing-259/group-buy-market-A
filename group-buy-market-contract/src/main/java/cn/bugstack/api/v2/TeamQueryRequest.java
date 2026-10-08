package cn.bugstack.api.v2;

public class TeamQueryRequest extends BaseRequest {
    private static final long serialVersionUID = 1L;
    private String teamId;
    private String userId;
    public TeamQueryRequest() {}
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
}
