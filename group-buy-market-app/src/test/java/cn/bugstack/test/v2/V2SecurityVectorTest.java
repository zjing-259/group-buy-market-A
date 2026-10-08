package cn.bugstack.test.v2;

import cn.bugstack.api.v2.TrialRequest;
import cn.bugstack.v2.V2Format;
import cn.bugstack.v2.V2Security;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class V2SecurityVectorTest {
    @Test public void canonicalDtoAndHmacMatchSharedContractVector() {
        TrialRequest request=new TrialRequest();
        request.setSource("s01"); request.setChannel("c01"); request.setUserId("\u7528\u6237A");
        request.setGoodsId("sku-1"); request.setProductVersion("2"); request.setQuantity(1);
        String canonical=new V2Security().canonicalJson(request);
        assertEquals("{\"activityId\":null,\"channel\":\"c01\",\"goodsId\":\"sku-1\","+
                "\"productVersion\":\"2\",\"quantity\":1,\"source\":\"s01\",\"userId\":\"\u7528\u6237A\"}",canonical);
        String hash=V2Format.sha256(canonical);
        assertEquals("16ee67b9b6441df3bf88995fea03003e5d81f82707bf3f8be9826868d7e6c21d",hash);
        String message=String.join("\n","zj-market","test-key-1","1790932800000",
                "AAAAAAAAAAAAAAAAAAAAAA","req-001","cn.bugstack.api.v2.MarketingService",
                "trial","2.0.0","gbm-dev",hash);
        assertEquals("uIBO9F14kK798WO51+IOE7RP2wrz3rvuRzwGVzpJnwc=",
                V2Security.hmac("test-secret-123",message));
    }
}
