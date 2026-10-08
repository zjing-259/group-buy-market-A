package cn.bugstack.v2;

import cn.bugstack.api.v2.RpcResult;
import cn.bugstack.api.v2.V2Codes;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

final class V2RpcResult {
    private V2RpcResult() {}
    static <T> RpcResult<T> call(Supplier<T> action) {
        String requestId=RpcContext.getServerAttachment().getAttachment("requestId");
        try { return new RpcResult<>(V2Codes.SUCCESS,"SUCCESS",action.get(),requestId); }
        catch (V2Failure known) { return new RpcResult<>(known.getCode(),known.getCode(),null,requestId); }
        catch (Exception unexpected) {
            LoggerFactory.getLogger(V2RpcResult.class).error("v2 RPC failed requestId:{}",requestId,unexpected);
            return new RpcResult<>(V2Codes.SYSTEM_ERROR,"INTERNAL_ERROR",null,requestId);
        }
    }
}
