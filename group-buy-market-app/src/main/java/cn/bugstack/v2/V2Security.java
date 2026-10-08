package cn.bugstack.v2;

import cn.bugstack.api.v2.BaseRequest;
import cn.bugstack.api.v2.V2Codes;
import cn.bugstack.api.v2.V2AuthenticationException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2Security {
    @Value("${ab.v2.rpc.client-id:}") private String clientId;
    @Value("${ab.v2.rpc.key-id:}") private String keyId;
    @Value("${ab.v2.rpc.secret:}") private String secret;
    @Value("${ab.v2.rpc.verification-keys:{}}") private String verificationKeys;
    @Value("${ab.v2.rpc.source:}") private String source;
    @Value("${ab.v2.rpc.channel:}") private String channel;
    @Value("${ab.v2.rpc.require-tls:true}") private boolean requireTls;
    @Resource private RedissonClient redis;
    private final ObjectMapper canonical = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    private static final Set<String> METHODS=new HashSet<>(Arrays.asList(
            "MarketingService.trial","TradeService.reserve","TradeService.query","TradeService.close",
            "TeamService.query","TeamService.queryReconciliation","TeamService.completeReconciliation",
            "IntegrationService.queryEventResult"));

    public static String hmac(String key,String message) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public String canonicalJson(Object dto) {
        try { return canonical.writeValueAsString(dto); }
        catch (Exception e) { throw new V2AuthenticationException(V2Codes.AUTH_INVALID); }
    }

    public void verify(Invoker<?> invoker,Invocation invocation) {
        if (secret==null || secret.isEmpty() || clientId.isEmpty() || keyId.isEmpty() || source.isEmpty() || channel.isEmpty())
            throw new V2AuthenticationException(V2Codes.AUTH_REQUIRED);
        if (requireTls && !Boolean.parseBoolean(invoker.getUrl().getParameter("ssl-enabled","false")))
            throw new V2AuthenticationException(V2Codes.AUTH_REQUIRED);
        Object[] args=invocation.getArguments();
        if (args==null || args.length!=1 || !(args[0] instanceof BaseRequest))
            throw new V2AuthenticationException(V2Codes.AUTH_INVALID);
        String iface=invoker.getInterface().getName();
        String method=invocation.getMethodName();
        if (!iface.startsWith("cn.bugstack.api.v2.") || !METHODS.contains(invoker.getInterface().getSimpleName()+"."+method))
            throw new V2AuthenticationException(V2Codes.FORBIDDEN);
        String requestClient=RpcContext.getServerAttachment().getAttachment("clientId");
        String requestKey=RpcContext.getServerAttachment().getAttachment("keyId");
        String timestamp=RpcContext.getServerAttachment().getAttachment("timestamp");
        String nonce=RpcContext.getServerAttachment().getAttachment("nonce");
        String requestId=RpcContext.getServerAttachment().getAttachment("requestId");
        String signature=RpcContext.getServerAttachment().getAttachment("signature");
        String verificationSecret=V2VerificationKeys.resolve(keyId,secret,verificationKeys,requestKey);
        if (!clientId.equals(requestClient) || verificationSecret==null || signature==null)
            throw new V2AuthenticationException(V2Codes.AUTH_INVALID);
        try {
            V2Format.id(requestId,64);
            if (timestamp==null || !timestamp.matches("[0-9]{13}")) throw new IllegalArgumentException();
            if (Math.abs(System.currentTimeMillis()-Long.parseLong(timestamp))>300000L) throw new IllegalArgumentException();
            if (nonce==null || Base64.getUrlDecoder().decode(nonce).length<16) throw new IllegalArgumentException();
        } catch (Exception e) { throw new V2AuthenticationException(V2Codes.AUTH_INVALID); }
        BaseRequest request=(BaseRequest)args[0];
        if (!source.equals(request.getSource()) || !channel.equals(request.getChannel()))
            throw new V2AuthenticationException(V2Codes.FORBIDDEN);
        String bodyHash=V2Format.sha256(canonicalJson(request));
        String text=String.join("\n",requestClient,requestKey,timestamp,nonce,requestId,iface,method,
                invoker.getUrl().getParameter("version",""),invoker.getUrl().getParameter("group",""),bodyHash);
        byte[] wanted=hmac(verificationSecret,text).getBytes(StandardCharsets.US_ASCII);
        byte[] actual=signature.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(wanted,actual)) throw new V2AuthenticationException(V2Codes.AUTH_INVALID);
        try {
            if (!redis.getBucket("ab:v2:rpc:nonce:"+clientId+":"+nonce).trySet("1",660,TimeUnit.SECONDS))
                throw new V2AuthenticationException(V2Codes.REPLAY_REJECTED);
        } catch (V2AuthenticationException rejection) { throw rejection; }
        catch (Exception unavailable) { throw new V2AuthenticationException(V2Codes.DEPENDENCY_UNAVAILABLE); }
    }

    public String clientId() { return clientId; }
    public String source() { return source; }
    public String channel() { return channel; }
}
