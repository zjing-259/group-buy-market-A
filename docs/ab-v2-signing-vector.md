# A/B v2 RPC 签名向量

此向量使用**公开测试密钥** `test-secret-123`，不能用于部署。双方可用它检查规范 JSON、UTF-8、SHA-256、LF 拼接和标准 Base64 的结果。对应可执行测试为 `V2SecurityVectorTest`。

接口 `cn.bugstack.api.v2.MarketingService`，方法 `trial`，group `gbm-dev`，version `2.0.0`。请求字段：source=`s01`、channel=`c01`、userId=`用户A`、goodsId=`sku-1`、quantity=`1`、productVersion=`2`、activityId=`null`。

规范请求 JSON（无空白，包含可选 null）：

```json
{"activityId":null,"channel":"c01","goodsId":"sku-1","productVersion":"2","quantity":1,"source":"s01","userId":"用户A"}
```

UTF-8 正文的 SHA-256 小写十六进制摘要：

```text
16ee67b9b6441df3bf88995fea03003e5d81f82707bf3f8be9826868d7e6c21d
```

签名附件 `clientId=zj-market`、`keyId=test-key-1`、`timestamp=1790932800000`、`nonce=AAAAAAAAAAAAAAAAAAAAAA`、`requestId=req-001`。签名原文由以下十行按单个 LF 连接，末尾没有 LF：

```text
zj-market
test-key-1
1790932800000
AAAAAAAAAAAAAAAAAAAAAA
req-001
cn.bugstack.api.v2.MarketingService
trial
2.0.0
gbm-dev
16ee67b9b6441df3bf88995fea03003e5d81f82707bf3f8be9826868d7e6c21d
```

以 `test-secret-123` 的 UTF-8 字节为 HMAC-SHA256 密钥，摘要使用标准 Base64，结果为：

```text
uIBO9F14kK798WO51+IOE7RP2wrz3rvuRzwGVzpJnwc=
```
