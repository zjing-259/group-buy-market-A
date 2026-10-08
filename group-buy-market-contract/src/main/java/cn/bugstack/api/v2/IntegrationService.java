package cn.bugstack.api.v2;

public interface IntegrationService {
    RpcResult<EventProcessingResult> queryEventResult(EventResultQueryRequest request);
}
