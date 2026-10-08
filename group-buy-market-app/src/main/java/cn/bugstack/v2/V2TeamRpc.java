package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import javax.annotation.Resource;

@DubboService(version="2.0.0",group="${ab.v2.dubbo-group:gbm-dev}",protocol="tri",timeout=10000,retries=0)
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2TeamRpc implements TeamService {
    @Resource private V2TeamEngine engine;
    @Override public RpcResult<Team> query(TeamQueryRequest request) {
        return V2RpcResult.call(() -> engine.query(request));
    }
    @Override public RpcResult<ReconciliationSnapshot> queryReconciliation(ReconciliationQueryRequest request) {
        return V2RpcResult.call(() -> engine.queryReconciliation(request));
    }
    @Override public RpcResult<ReconciliationResult> completeReconciliation(ReconciliationCompleteRequest request) {
        return V2RpcResult.call(() -> engine.complete(request));
    }
}
