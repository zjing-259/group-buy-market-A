package cn.bugstack.v2;

import cn.bugstack.api.v2.V2AuthenticationException;
import cn.bugstack.api.v2.RpcResult;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.RpcContext;

@Activate(group=CommonConstants.PROVIDER,order=-10000)
public class V2ProviderFilter implements Filter {
    @Override public Result invoke(Invoker<?> invoker,Invocation invocation) {
        if (invoker.getInterface().getName().startsWith("cn.bugstack.api.v2.")) {
            try { V2SpringBridge.get(V2Security.class).verify(invoker,invocation); }
            catch (V2AuthenticationException rejected) {
                String requestId=RpcContext.getServerAttachment().getAttachment("requestId");
                return AsyncRpcResult.newDefaultAsyncResult(
                        new RpcResult<>(rejected.getCode(),rejected.getCode(),null,requestId),invocation);
            }
        }
        return invoker.invoke(invocation);
    }
}
