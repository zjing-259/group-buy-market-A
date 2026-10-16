package cn.bugstack.api.v2;

/** Read-only, authenticated discovery; a result never guarantees a reservation. */
public interface TeamDiscoveryService {
    RpcResult<TeamDiscoveryPage> list(TeamDiscoveryRequest request);
}
