package cn.bugstack.api.v2;

public interface TradeService {
    RpcResult<Reservation> reserve(ReserveRequest request);
    RpcResult<Reservation> query(TradeQueryRequest request);
    RpcResult<CloseResponse> close(CloseRequest request);
}
