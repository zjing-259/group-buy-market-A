package cn.bugstack.api.v2;

public interface TeamService {
    RpcResult<Team> query(TeamQueryRequest request);
    RpcResult<ReconciliationSnapshot> queryReconciliation(ReconciliationQueryRequest request);
    RpcResult<ReconciliationResult> completeReconciliation(ReconciliationCompleteRequest request);
}
