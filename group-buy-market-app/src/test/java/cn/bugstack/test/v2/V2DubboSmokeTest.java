package cn.bugstack.test.v2;

import cn.bugstack.api.v2.EventProcessingResult;
import cn.bugstack.api.v2.EventResultQueryRequest;
import cn.bugstack.api.v2.IntegrationService;
import cn.bugstack.api.v2.RpcResult;
import cn.bugstack.v2.V2Format;
import cn.bugstack.v2.V2Security;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/** Run explicitly with the local v2 provider started against the isolated MySQL schema. */
public class V2DubboSmokeTest {
    private ReferenceConfig<IntegrationService> reference() {
        ReferenceConfig<IntegrationService> reference=new ReferenceConfig<>();
        reference.setApplication(new ApplicationConfig("v2-smoke-client"));
        reference.setInterface(IntegrationService.class);
        reference.setUrl("tri://127.0.0.1:50051");
        String certificate=System.getenv("AB_V2_TEST_TLS_CERT");
        if (certificate!=null) {
            org.apache.dubbo.config.SslConfig ssl=new org.apache.dubbo.config.SslConfig();
            ssl.setClientKeyCertChainPath(certificate); ssl.setClientTrustCertCollectionPath(certificate);
            ssl.setClientPrivateKeyPath(System.getenv("AB_V2_TEST_TLS_KEY"));
            reference.getApplicationModel().getApplicationConfigManager().setSsl(ssl);
            reference.setUrl("tri://127.0.0.1:50051?ssl-enabled=true");
        }
        reference.setGroup("gbm-dev"); reference.setVersion("2.0.0");
        reference.setRetries(0); reference.setTimeout(10000);
        return reference;
    }

    @Test public void signedTripleQueryReachesBusinessHandler() {
        String secret=System.getenv("AB_V2_RPC_SECRET");
        Assume.assumeTrue(secret!=null && !secret.isEmpty());
        ReferenceConfig<IntegrationService> reference=reference();
        try {
            IntegrationService service=reference.get();
            EventResultQueryRequest request=new EventResultQueryRequest();
            request.setSource("s01"); request.setChannel("c01"); request.setEventId("missing-"+UUID.randomUUID());
            String client=System.getenv("AB_V2_RPC_CLIENT_ID");
            String keyId=System.getenv("AB_V2_RPC_KEY_ID");
            String requestId=UUID.randomUUID().toString();
            String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(
                    UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            String timestamp=Long.toString(System.currentTimeMillis());
            String bodyHash=V2Format.sha256(new V2Security().canonicalJson(request));
            String message=String.join("\n",client,keyId,timestamp,nonce,requestId,
                    IntegrationService.class.getName(),"queryEventResult","2.0.0","gbm-dev",bodyHash);
            RpcContext.getClientAttachment().setAttachment("clientId",client);
            RpcContext.getClientAttachment().setAttachment("keyId",keyId);
            RpcContext.getClientAttachment().setAttachment("timestamp",timestamp);
            RpcContext.getClientAttachment().setAttachment("nonce",nonce);
            RpcContext.getClientAttachment().setAttachment("requestId",requestId);
            RpcContext.getClientAttachment().setAttachment("signature",V2Security.hmac(secret,message));
            RpcResult<EventProcessingResult> result=service.queryEventResult(request);
            assertEquals("NOT_FOUND",result.getCode());
            assertEquals(requestId,result.getRequestId());
            org.junit.Assert.assertNull(result.getData());
        } finally { reference.destroy(); }
    }

    @Test public void invalidSignatureIsReturnedAsExplicitAuthenticationFailure() {
        Assume.assumeTrue(System.getenv("AB_V2_RPC_SECRET")!=null);
        ReferenceConfig<IntegrationService> reference=reference();
        try {
            IntegrationService service=reference.get();
            EventResultQueryRequest request=new EventResultQueryRequest();
            request.setSource("s01"); request.setChannel("c01"); request.setEventId("product-applied");
            RpcContext.getClientAttachment().setAttachment("clientId",System.getenv("AB_V2_RPC_CLIENT_ID"));
            RpcContext.getClientAttachment().setAttachment("keyId",System.getenv("AB_V2_RPC_KEY_ID"));
            RpcContext.getClientAttachment().setAttachment("timestamp",Long.toString(System.currentTimeMillis()));
            RpcContext.getClientAttachment().setAttachment("nonce",Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
            RpcContext.getClientAttachment().setAttachment("requestId",UUID.randomUUID().toString());
            RpcContext.getClientAttachment().setAttachment("signature","invalid-signature");
            RpcResult<EventProcessingResult> rejected=service.queryEventResult(request);
            assertEquals("AUTH_INVALID",rejected.getCode());
            org.junit.Assert.assertNull(rejected.getData());
        } finally { reference.destroy(); }
    }
}
