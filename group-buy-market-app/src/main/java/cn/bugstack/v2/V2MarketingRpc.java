package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import javax.annotation.Resource;

@DubboService(version="2.0.0",group="${ab.v2.dubbo-group:gbm-dev}",protocol="tri",timeout=10000,retries=0)
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2MarketingRpc implements MarketingService {
    @Resource private V2MarketingEngine engine;
    @Override public RpcResult<TrialResponse> trial(TrialRequest request) {
        return V2RpcResult.call(() -> engine.trial(request));
    }
}
