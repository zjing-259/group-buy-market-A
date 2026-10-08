package cn.bugstack.api.v2;

public interface MarketingService {
    RpcResult<TrialResponse> trial(TrialRequest request);
}
