package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import javax.annotation.Resource;

@DubboService(version="2.0.0",group="${ab.v2.dubbo-group:gbm-dev}",protocol="tri",timeout=10000,retries=0)
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2TradeRpc implements TradeService {
    @Resource private V2TradeEngine engine;
    @Override public RpcResult<Reservation> reserve(ReserveRequest request) {
        return V2RpcResult.call(() -> engine.reserve(request));
    }
    @Override public RpcResult<Reservation> query(TradeQueryRequest request) {
        return V2RpcResult.call(() -> engine.query(request));
    }
    @Override public RpcResult<CloseResponse> close(CloseRequest request) {
        return V2RpcResult.call(() -> engine.close(request));
    }
}
