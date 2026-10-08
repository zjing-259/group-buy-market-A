package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import javax.annotation.Resource;

@DubboService(version="2.0.0",group="${ab.v2.dubbo-group:gbm-dev}",protocol="tri",timeout=10000,retries=0)
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2IntegrationRpc implements IntegrationService {
    @Resource private V2EventEngine engine;
    @Resource private V2Security security;
    @Override public RpcResult<EventProcessingResult> queryEventResult(EventResultQueryRequest request) {
        return V2RpcResult.call(() -> engine.query(request,security.clientId()));
    }
}
