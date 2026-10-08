# A/B服务接口契约

版本：v2契约基线1.1（Dubbo3 + RocketMQ），2026-10-02。状态：D1～D3已由用户选择，供A/B共同开发；本版替代1.0的A/B HTTP通信约定。A侧实现已完成隔离业务、实际TLS/RPC/MQ及独立Boot4夹具验收，实际B全流程和生产部署尚未完成，见 [TASK_PROGRESS.md](../TASK_PROGRESS.md)。具体技术参数为本契约约定，不代表已经完成部署验收。

本文对应A-TODU的API-001～008及完整交易所需FEAT-001～004。A为拼团营销服务，B为商城服务；统一前端访问B。现有v1和65项回归是兼容基线，新增v2不能静默改变历史支付事实。

## 1. 已确认的业务决策

| 决策 | 推荐方案 | 备选方案 | 影响 |
|---|---|---|---|
| D1 基础价格传递 | B向A同步带版本商品资料；A本地计算，版本不符拒绝新占位 | B每次向A传入可信基础价 | 商品同步接口、试算/锁单入参、版本校验 |
| D2 团队到期与迟到支付消息 | 到期进入核对状态，B关闭支付并核对所有订单后确定结果；故障允许等待 | 固定宽限期后终结，后到支付退款且不恢复团队 | 关闭协议、失败时刻、支付接受规则、用户等待时长 |
| D3 支付后主动退出 | 未支付可取消，已支付不主动退团，拼团失败自动退款 | 成团前允许已支付退团退款 | 计数回退、成团竞争、退款接口和库存释放 |

用户已于2026-10-02选择D1、D2、D3的推荐方案，备选方案仅保留作为决策历史。其他首版范围沿用维护清单：单SKU、数量1、CNY、一笔支付对应一笔商城订单、不叠加B额外优惠。团队限制最多100人，支持一次返回完整核对名单；更大团需另行扩展快照分页协议，不静默截断。

## 2. 数据责任与一致性

- B拥有商品基础资料/基础售价、用户身份、库存、商城订单、真实支付和退款、履约。A拥有活动优惠、营销资格、参团记录、团队和成团事实。禁止跨库读写。
- B生成稳定outTradeNo；A参团业务键为(source,channel,outTradeNo)。每个source/channel只授权一个clientId，渠道转移须处理历史授权，不能直接复用。
- 所有新交易先在B本地事务保存商城订单、库存预留和后续任务，再调用A占位。A成功后B保存A的成交快照才开放支付。
- B实际支付金额等于A固化payPrice。支付结果先由B验证并持久化，再通过RocketMQ发布支付事实；Broker发送确认不等于A已接受支付，A的处理结果事件或权威查单才用于核对业务结果。
- A支付明细、人数、成团状态和事件落库保持本地事务。B按eventId持久化接收并幂等更新订单，成团前不得履约。
- 超时/断连是结果未知；保留业务键查单/重试。NOT_FOUND不证明仍在执行的写请求不会提交。释放库存必须有确定关闭依据。
- 各自本地任务支持重启恢复。新链路使用Outbox发布重试、MQ消费重试/死信及业务查单补偿；旧HTTP通知最多5次的规则仅保留给历史HTTP任务，不直接套用到MQ链路。

## 3. 通信与通用格式

- 浏览器→B：保留HTTP/JSON及B用户登录。B→A同步业务：Dubbo3、Triple协议、Java接口方式；异步事实：RocketMQ普通消息。新A/B契约不再以REST路径或HTTP回调作为主入口。
- A导出服务，B引用；契约Maven包建议 `cn.bugstack:group-buy-market-contract`，包名 `cn.bugstack.api.v2`，仅含RPC接口、POJO、事件类型和业务码，禁止携带领域实现、DAO、Spring依赖或数据库实体。Java 8可用语法/字节码，避免强迫两端升级同一JDK。
- Dubbo service version=`2.0.0`、group=`gbm-${env}`，protocol显式设为`tri`。首轮直连，服务发现以后独立接入；注册中心不属于本次必须新增项。端口、地址、凭据由外部配置提供。
- 当前A为Java8/Spring Boot2.7.12，B的pom为Java17/Spring Boot4.1.1。A已固定Dubbo3.2.16与RocketMQ4.9.7；独立Boot4夹具使用原始SDK和Netty4.1.118.Final，在JDK21/release17下验证启动、Triple DTO/认证附件、TLS/mTLS与MQ互通；具体证据见[兼容矩阵](phase5-ab-v2.md)。尚未验证实际B及精确JDK17运行时，也未证明同一Starter兼容两端。必要时用独立SDK配置，版本调整不得悄悄混入业务改造。
- 所有标识符为精确匹配字符串，拒绝首尾空白；activityId和数值版本使用无前导零的十进制正整数字符串，范围1～9223372036854775807，与V2Format的Long解析一致；rosterVersion为不透明的名单版本标识，沿用64字符ID约束。v2 userId/goodsId/outTradeNo/paymentId/refundId/requestId/orderId/teamId/eventId上限64字符，source/channel上限8。须迁移现有短字段、相关索引和标签成员列，不能只改DTO。
- 金额在线上DTO及消息中统一为两位小数字符串，currency=CNY。原价、应付、实付及本协议全额退款金额均为0.01～999999.99；优惠额允许0.00，且不超过原价减0.01，应付=原价−优惠。不允许零元购。内部BigDecimal计算，优惠计算结果按HALF_UP保留两位，再统一校验和保存快照；消息不接受额外精度或由接收方静默舍入。quantity固定1。
- 时间为UTC ISO-8601毫秒字符串；有效支付时间为 `[reservedAt,paymentDeadline)`，不得接受未来支付。截止前发生但迟到的事实允许在RECONCILING接收。
- RPC返回 `RpcResult<T>{code,info,data,requestId,retryAfterMillis?}`，成功0000，业务失败data=null；异常分类见第7节。没有A/B HTTP状态码和HTTP Retry-After约定。B自行将结果映射为前端HTTP响应。
- A参团状态RESERVED/PAID/CANCELLED，团队状态FORMING/RECONCILING/COMPLETED/FAILED；退款独立refundStatus。recordVersion/teamVersion为单调递增字符串。返回当前状态不破坏不变成交快照的幂等性。
- 业务幂等键与网络尝试分离：一次RPC重试生成新requestId/nonce；MQ重投保持原eventId和正文，Broker messageId可能改变，不用它做业务幂等键。

## 4. Dubbo同步接口

每个方法仅一个明确类型请求POJO、不重载、不使用任意Map或框架实体。下表方法的返回类型均为 `RpcResult<对应响应DTO>`；接口全名为 `cn.bugstack.api.v2.<接口名>`，字段以本文为准。

| 服务/方法 | 请求DTO | 必填业务输入（均含source/channel） | 输出data |
|---|---|---|---|
| MarketingService.trial | TrialRequest | userId,goodsId,quantity,productVersion；activityId可选 | TrialResponse：available,visible,eligible,reasonCode,offer或null,quotedAt |
| TradeService.reserve | ReserveRequest | userId,outTradeNo,goodsId,activityId,quantity,productVersion,expectedPayPrice,currency；teamId可选 | Reservation |
| TradeService.query | TradeQueryRequest | userId,outTradeNo | Reservation含payment/refund |
| TeamService.query | TeamQueryRequest | teamId；userId可选 | Team含本人参团摘要 |
| TradeService.close | CloseRequest | 第8.2节关闭字段 | CloseResponse |
| TeamService.queryReconciliation | ReconciliationQueryRequest | teamId | ReconciliationSnapshot，第8.3节 |
| TeamService.completeReconciliation | ReconciliationCompleteRequest | 第8.3节核对提交字段 | ReconciliationResult |
| IntegrationService.queryEventResult | EventResultQueryRequest | eventId | EventProcessingResult或业务NOT_FOUND |

额外queryEventResult用于B查询自己发布的商品、支付、退款事件在A的持久处理结果，不能跨clientId/source/channel查看事件。它为后台权限，不向浏览器透传。EventProcessingResult含eventId、eventType、processingStatus（当前实现返回WAITING_DEPENDENCY/PROCESSED；隔离消息单独保存，不返回QUARANTINED业务结果）、outcome/code/info/processedAt/result（WAITING_DEPENDENCY时尚无完成结果；PROCESSED时APPLIED/IGNORED携带对应结构的result，REJECTED的result为null），不返回敏感原始正文。未知身份或格式破损、无法可信归属的隔离消息不通过此接口暴露。NOT_FOUND只表示尚未记录结果，不能解释为事实被拒绝。

所有交易请求userId由B登录授权后确定；调用方权限不能靠请求体声明。合法历史查单不受当前营销准入黑名单/切量影响；团队详情不包含他人订单或支付流水。

offer含activityId、goodsId、originalPrice、deductionPrice、payPrice、currency、targetCount、productVersion。无活动正常available=false；不可见不返回专属信息；系统故障不伪装成无活动。试算/新占位必须匹配A本地商品版本且商品在售；试算不保价，新占位应付与expectedPayPrice不符返回PRICE_CHANGED，无新增占位。

Reservation含recordKind、orderId、outTradeNo、userId、source、channel、activityId、goodsId、productVersion、quantity、teamId、originalPrice、deductionPrice、payPrice、currency、reservedAt、paymentDeadline、orderStatus、teamStatus、recordVersion、teamVersion、serverTime、payment或null、refund或null。payment含paymentId、paidAmount、currency、paidAt；refund含refundId、refundStatus、refundVersion、refundAmount、currency，未退款时null。历史事实缺失返回LEGACY_FACT_UNVERIFIABLE，不补造。

关闭先于占位时，查询返回recordKind=CLOSE_MARKER、CANCELLED、userId/source/channel/outTradeNo、closedAt/recordVersion，未产生的订单/团队/金额/期限为null；正常记录为RESERVATION。不能将关闭标记误报NOT_FOUND。

Team含teamId、activityId、goodsId、source、channel、targetCount、reservedCount、paidCount、teamStatus、teamVersion、validStartTime、validEndTime、joinable、serverTime。reservedCount含已支付的有效占位；joinable不是占位承诺。

占位同键比较用户、渠道、商品、活动、请求teamId、数量、expectedPayPrice、productVersion；异参E0107。先回读原结果再校验当前配置，重试不能按新价格重算。空teamId开团，有值参团。已有占位维持旧金额，同团成员不要求成交价相同。

所有RPC显式 `retries=0`，写操作使用单次失败即返回策略（不使用广播、并发竞速、后台自动补发），由应用查单和原业务键恢复；读请求也先关闭框架重试，防止初学阶段多层重试放大。初始单次timeout=10000ms，服务端仍可能在客户端超时后提交。试算5秒仅是现有并行查询预算，不是所有RPC总耗时保证。

## 5. RocketMQ事件契约

### 5.1 Topic、Tag和消费组

下表逻辑名称统一附加 `-${env}` 后缀，开发/测试/部署环境完全隔离。首版均为NORMAL Topic、并发消费，不依赖FIFO或延迟消息保证业务正确性；一个消费者组内所有实例订阅一致。

| Topic | Tag（等于eventType） | 生产者 | 消费者组 | 用途 |
|---|---|---|---|---|
| B_PRODUCT_EVENTS | PRODUCT_SNAPSHOT_CHANGED | B | A_PRODUCT_SYNC | 带版本商品全量快照 |
| B_TRADE_EVENTS | PAYMENT_SUCCEEDED、REFUND_STATUS_CHANGED | B | A_TRADE_FACTS | 接收支付事实与退款状态 |
| A_TEAM_EVENTS | TEAM_COMPLETED、TEAM_RECONCILIATION_REQUIRED、TEAM_FAILED | A | B_TEAM_EVENTS | 履约、核对、失败退款 |
| A_INTEGRATION_RESULTS | EVENT_PROCESSING_RESULT | A | B_INTEGRATION_RESULTS | 告知B事实是否已被A处理 |

Message Key设为eventId，业务单号/团队号另作可检索属性；正文才是权威业务输入，属性必须与正文一致。相同业务的多实例共用消费组，不能每实例创建独立组导致全量重复消费；不同业务订阅者需新组且单独幂等。实际重试和死信系统Topic由所选客户端/Broker机制管理，不手工复制成普通业务Topic。

### 5.2 公共信封及payload

所有消息为UTF-8 JSON：`{eventId,eventType,schemaVersion,producer,occurredAt,source,channel,correlationId,payload}`。schemaVersion首版1；producer为已配置clientId；correlationId关联来源请求/事件，允许null。eventId及正文在本地业务事务生成、保存，occurredAt为事实发生时间，不是重发时间。消息正文上限256KiB，核对名单最多100项，不截断成员。schemaVersion与Dubbo服务版本、商品业务版本互相独立。

| eventType | payload字段 |
|---|---|
| PRODUCT_SNAPSHOT_CHANGED | goodsId,productVersion,goodsName,originalPrice,currency,saleStatus；见8.1 |
| PAYMENT_SUCCEEDED | userId,outTradeNo,paymentId,paidAmount,currency,paidAt |
| REFUND_STATUS_CHANGED | userId,outTradeNo,paymentId,refundId,refundAmount,currency,refundStatus,refundVersion,occurredAt,failureCode?；异常收款额外paidAt/paidAmount，见8.4 |
| TEAM_COMPLETED | teamId,activityId,teamVersion,outTradeNoList；完整已接受支付名单 |
| TEAM_RECONCILIATION_REQUIRED | teamId,activityId,teamVersion,outTradeNoList,reconciliationId,rosterVersion,validEndTime |
| TEAM_FAILED | teamId,activityId,teamVersion,outTradeNoList,reconciliationId,reason,refundRequiredOutTradeNoList |
| EVENT_PROCESSING_RESULT | originalEventId,originalEventType,outcome,code,info,processedAt,result或null |

A处理支付时校验完整用户/渠道/金额/币种/支付时间和paymentId；同事实不重复计数，异事实E0113，paymentId在B调用方范围内唯一绑定一单。首版不支持零元订单；B不得发送零元支付事实，实付最低0.01元。结果中的支付result为orderId/outTradeNo/teamId/payment/orderStatus/teamStatus/recordVersion/teamVersion；商品result为goodsId/currentVersion/applied/saleStatus；退款result为outTradeNo/refundId/refundStatus/refundVersion/recordVersion。

结果outcome为APPLIED（本次已应用）、IGNORED（旧版本/相同事实已存在）、REJECTED（确定性业务拒绝）。 EVENT_PROCESSING_RESULT的originalEventType仅允许PRODUCT_SNAPSHOT_CHANGED、PAYMENT_SUCCEEDED、REFUND_STATUS_CHANGED。APPLIED/IGNORED必须有完整非空result和code=0000；REJECTED必须显式result=null且code非0000。所有结果字段均必填，拒绝未知字段，包括嵌套payment；商品result.applied在APPLIED时为true、IGNORED时为false。支付result.orderStatus=PAID，teamStatus为FORMING/RECONCILING/COMPLETED/FAILED之一；支付流水、金额、币种、时间及版本使用相同通用格式。同eventId网络重投返回原持久结果，不重算outcome；新eventId重复同一业务事实则IGNORED。code成功0000，拒绝使用第7节业务码。A消费每个B事实时，把业务变更或拒绝记录、Inbox结果、结果事件Outbox放在同一事务。结果事件本身不触发另一个结果事件，避免循环确认。

B保存“已支付、A确认待定”直至APPLIED/IGNORED结果或权威查单确认；MQ发送成功不准改为A已结算。结果事件丢失使用queryEventResult，再结合TradeService.query核对。业务拒绝进入异常处理，不把真实付款改为未付款。商品结果仅更新同步进度，不能将B主数据改成A较旧状态。

### 5.3 发布、消费、重试与死信

1. 首版统一**本地Outbox + RocketMQ普通消息**。B商品/支付/退款提交与Outbox同事务，A成团/核对/失败及处理结果与Outbox同事务。发布器数据库租约认领，Broker确认后标记PUBLISHED；崩溃窗口允许重投，eventId不变。发送超时保留待发送，不猜测Broker未收到。
2. Outbox失败按1/2/4/8分钟退避，之后每8分钟重试；达到5次告警但不永久丢弃，支持人工暂停并保留待恢复事实。SQL计时与租约校验延用先锁行后检查时间的已验证边界，SDK内部发送重试和数据库重试分层统计。PUBLISHED不是下游已应用；发布记录及原正文必须保留覆盖业务核对/人工重放周期。结果长期缺失先查询、检查消费积压/死信，再受控以原eventId重发，不能盲目持续复制消息。
3. 消费端Inbox唯一键为(消费业务,producer,eventId)，保存正文摘要及结果；业务更新与Inbox提交同事务，提交后才返回消费成功。不同eventId的同paymentId仍受业务唯一约束。相同eventId异正文持久化隔离并告警，不覆盖原结果。
4. 短暂数据库/网络故障抛出消费失败，由RocketMQ重试；不能catch后无条件成功。选用的SDK必须配置明确的有限重试策略并实测死信路径，部署记录实际次数与间隔，不依赖版本默认值。失败进入死信后告警、定位、原eventId受控重放并查单，不把“进入死信”当作业务结束。
5. 确定性金额/身份/事实冲突：A事务内持久化REJECTED及结果事件后确认消费，不无限重试。依赖消息尚未到达（例如退款先于核对落库）进入持久WAITING_DEPENDENCY任务后才可确认；后台自动重查并恢复处理，不能只靠MQ有限重试耗尽。未应用业务时不能留下会永久跳过后续处理的PROCESSED标记。
6. 版本旧消息幂等忽略；新版本按锁/条件更新防倒退。TEAM消息乱序或本地订单缺失，B持久保存待核对任务并查A，不能直接丢弃；即使忽略旧状态，也须检查该事件所需本地业务动作是否已完成。B验证本地资金后才履约。
7. JSON不可解析、未知schema、来源/签名错误等毒消息先持久隔离及告警；隔离落库失败不得确认。未知版本不伪造业务拒绝结果；部署兼容后可受控重放。未处理Inbox、死信、积压Outbox和RECONCILING均需监控。
8. RocketMQ事务消息作为后续独立学习实验，**不与当前同一事实Outbox双发**。它不能替代消费者事务、幂等或A/B对账；首版不引入分布式数据库事务。

## 6. 服务身份与权限

### 6.1 Dubbo

Triple传输启用TLS，受控开发环境例外需显式配置。Consumer/Provider Filter传递和验证RPC身份附件：clientId、keyId、timestamp（Unix毫秒）、nonce（至少128位随机数编码）、requestId、signature。这不是直接复用旧HTTP header签名。

采用HMAC-SHA256，原文为以下字段LF连接、末尾无LF：clientId、keyId、timestamp、nonce、requestId、完整接口名、方法名、service version、group、请求DTO规范JSON的SHA-256小写十六进制摘要。签名标准Base64。DTO规范JSON约定：字段名递归字典序、包含契约可选字段的null、数组保留顺序、不插入空白、字符串按JSON规则转义且不做Unicode归一化，禁止重复键/任意扩展Map；数值仅quantity等整数字段，其余金额/ID/版本为字符串。两端共享明确的规范序列化器和签名向量，不能对各自默认序列化结果直接签名。

验签后校验clientId到source/channel及接口方法的权限，300秒时钟窗口，Redis SET NX记录(clientId,nonce)、TTL660秒。时间/签名先检查再消耗nonce，防重放存储不可用则拒绝调用。应用重试重新签名，保留原业务键。注册中心注册成功、Dubbo应用名和请求体userId均不能代替身份认证；B负责真实用户登录，A继续校验业务用户归属。

### 6.2 RocketMQ

Broker按A/B独立凭据设置Topic发布、消费组订阅最小权限，禁止匿名及互相发布对方Topic；Tag是路由条件，不是授权边界。密钥外部配置；生产传输加密及SDK/Broker认证方式随兼容验证落地。

消息属性另带keyId、signature；HMAC-SHA256原文为实际Topic名、LF、原始正文SHA-256小写摘要，使用独立消息密钥。producer必须与keyId所属服务一致，Topic/Tag/eventType/渠道权限都校验。原始正文在Outbox保存，发布重试不重新拼装；可信发布器可在轮换后用新keyId重新签相同正文。保留旧验签密钥覆盖消息保留期及人工回放期，提前删除需制定恢复路径。

**MQ消息不采用RPC的300秒窗口和nonce一次性拦截**：延迟消费及合法重投允许发生，以eventId/版本/业务状态防重复效果。未知keyId不能跳过验签；进入可恢复隔离，补齐可信配置后重放。

B交易身份无DCC管理权限。旧v1仍需受控认证/网络访问，不能成为RPC旁路。日志仅记录requestId/eventId、业务号和脱敏错误，不输出凭据及完整敏感正文。

## 7. 业务错误、RPC异常与消息结果

业务码保持跨传输一致；Dubbo业务拒绝返回RpcResult非0000，异步拒绝记入EVENT_PROCESSING_RESULT。A内部堆栈/SQL异常不作为业务成功，也不直接序列化给B。

| 类别 | code | 动作 |
|---|---|---|
| 参数 | 0002 / UNSUPPORTED_CURRENCY | 修正请求，不自动改参重试 |
| 认证/授权 | AUTH_REQUIRED / AUTH_INVALID / REPLAY_REJECTED / FORBIDDEN / E0112 | 拒绝业务执行，检查凭据/授权；MQ毒消息按隔离流程处理 |
| 不存在 | NOT_FOUND / PRODUCT_NOT_FOUND | 查不到不代表延迟写请求不会提交 |
| 幂等冲突 | E0107 / E0113 / PAYMENT_ID_CONFLICT | 不换业务键规避，进入核对 |
| 活动/资格 | E0108 / E0109 / E0101 / E0102 / E0103 / E0110 / E0105 | 遵循业务规则；已接受事实重放不重新准入 |
| 价格/支付 | E0106 / PRICE_CHANGED / PRODUCT_VERSION_MISMATCH / PAYMENT_AMOUNT_MISMATCH / PAYMENT_OUTSIDE_WINDOW | 未占位可重新确认；已付款进入核对/退款而非改为未付款 |
| 商品 | PRODUCT_VERSION_CONFLICT / PRODUCT_NOT_SALEABLE | 版本冲突告警或拒绝新购买 |
| 关闭 | CLOSE_CONFLICT / ORDER_ALREADY_PAID / ORDER_CLOSED | 状态核对，不反转已关闭/已支付事实 |
| 核对 | RECONCILIATION_NOT_STARTED / RECONCILIATION_CONFLICT / RECONCILIATION_INCOMPLETE | 等待确定事实或修正提交，不伪造终态 |
| 退款/历史 | REFUND_CONFLICT / LEGACY_FACT_UNVERIFIABLE | 人工或受控后台核对 |
| 临时不可用 | DEPENDENCY_UNAVAILABLE / E0003 / E0004 | 原业务键恢复；MQ依赖故障不永久拒绝。retryAfterMillis为预留可选字段，当前未实现限流或依赖超时提示；不声明RATE_LIMITED/DEPENDENCY_TIMEOUT已可返回 |
| 系统/配置 | 0001 / E0111 | 告警，查询写入结果，禁止泄露内部堆栈 |

连接失败、无可用Provider、RPC超时、序列化失败归为RPC技术异常，由B适配层统一处理，不凭异常宣称事务回滚。参数/业务冲突不框架重试，写入结果未知先查单再原键重放。若Provider Filter直接拒绝而无法返回RpcResult，必须映射为明确认证异常，不混同业务成功。

消息发送失败、消费技术失败、确定性业务拒绝是三种不同结果。Broker发送成功只可标记Outbox已发布，消费成功只表示消费者已持久承担该消息；只有APPLIED/IGNORED或权威状态证明业务已完成，WAITING_DEPENDENCY不能当成已结算。

## 8. 商品、关闭、到期核对与退款协议

以下约束适用于已确认方案：

1. 取消先于延迟占位到达时，A按业务键保存关闭标记，之后该键不得重新建单。重复关闭幂等；同单关闭和支付确认在同一订单锁语义下裁决，不允许同时成功。
2. B先停止支付入口，并从资金渠道获取明确关闭/支付结果；渠道结果未知不能声称未付款。A关闭回执不能代替资金渠道关单。
3. A接受支付后不得按未支付路径关闭；B不能在库存已释放后无条件恢复销售承诺。已经成团的团队不因迟到命令反转。
4. 团队最终失败后不恢复成团；B处理已支付订单退款，退款幂等键稳定。退款失败保留可见状态并重试，不能伪报退款成功。
5. B区分无履约可能后的库存释放与退款进度；两者分别记录并幂等，不能把退款回调重复当作重复释放理由。
6. 商品同步全量快照、单调版本、同版本异内容冲突、乱序忽略及禁售规则见下文；新占位须匹配B版本，历史占位维持原金额。

### 8.1 商品同步：PRODUCT_SNAPSHOT_CHANGED（B→A）

消息信封含source/channel，payload含goodsId、productVersion（大于0的单调整数字符串）、goodsName（最多128字符）、originalPrice、currency=CNY、saleStatus（ON_SALE/OFF_SALE）。这是全量营销副本，不含库存。A处理结果result为goodsId、currentVersion、applied、saleStatus，通过结果事件或queryEventResult取得。

按(source,channel,goodsId)隔离商品。更大版本原子覆盖；同版本同内容幂等成功applied=false，同版本异内容拒绝PRODUCT_VERSION_CONFLICT；旧版本忽略并记录当前版本/applied=false的处理结果，不覆盖新值。全部营销依赖字段、禁售状态变化都递增版本。B提交商品变更和待同步任务同事务；A不能依靠同步消息到达顺序。无需每次商品变更要求A同步在线。

试算/占位查不到商品返回PRODUCT_NOT_FOUND，版本不匹配PRODUCT_VERSION_MISMATCH，禁售PRODUCT_NOT_SALEABLE，B完成同步后重试新试算/占位。B创建商城订单时再次检查本地在售状态和当前版本，防止其发出过时版本的新购买。现有按goodsId单独读取SKU的实现必须增加渠道范围或施加等价唯一约束；不得让不同渠道商品副本互相覆盖。

### 8.2 未支付关闭：TradeService.close（B→A RPC）

入参：source、channel、userId、outTradeNo、closeId（64字符内稳定操作ID）、reason（USER_CANCEL/PAYMENT_TIMEOUT/STOCK_FAILURE/ORDER_ABORT）、paymentDisposition（NEVER_OPENED/CLOSED_UNPAID）、verifiedAt。后两字段由可信B声明并保存资金渠道核验依据，不接受浏览器传入。

出参：outTradeNo、orderId或null、teamId或null、orderStatus=CANCELLED、closedAt、recordVersion、teamVersion或null。不存在参团记录时也按业务键和用户保存关闭标记并返回成功，防止延迟reserve建单。用户不同不能复用标记；重复closeId异参返回CLOSE_CONFLICT；同单已关闭且同用户的新closeId可以返回既有关闭结果，不重复释放。

已PAID返回ORDER_ALREADY_PAID，不支持主动退团；同键迟到reserve返回ORDER_CLOSED，迟到支付确认返回ORDER_CLOSED并由B核对收款退款。未支付关闭和确认支付以数据库锁串行裁决，业务键守卫→参团行→团队行保持统一顺序，不引入相反的锁序。关闭将有效占位人数减少一次、保留历史明细；取消不返还活动参与次数额度，避免反复占位绕过限次。FORMING或RECONCILING的未支付记录可关闭，未到截止的空团仍按原期限保留。

B收到确定CANCELLED后可关闭商城订单并原子释放本地库存；资金状态UNKNOWN时不得发送CLOSED_UNPAID。库存预留失败且从未对A发请求则无需建A占位；若A请求可能已发出，使用本关闭接口消除迟到请求。

### 8.3 到期核对

A在数据库团队锁内检查当前时间，过期FORMING变为RECONCILING，禁止新增占位；同事务创建TEAM_RECONCILIATION_REQUIRED事件。支付在截止前且在reservedAt之后发生的事实，RECONCILING仍允许接收；正常交易既有用户/渠道/金额/流水校验继续执行。已接受事实重放不受终态或后续黑名单影响；新的支付准入被阻止时保留待核对并告警，不把真实支付伪报为未支付。

为核对生成稳定reconciliationId（64字符内）及不可变rosterVersion，冻结当时未取消的完整参团名单。后续关闭只改变名单成员状态，不删除名单；名单成员数量最多100。事件outTradeNoList为该名单全部单号，增加reconciliationId、rosterVersion、validEndTime。快照不包括已提前取消的记录，其支付异常由B自己的关闭流程负责。

`TeamService.queryReconciliation` 入参source、channel、teamId，出参teamStatus、teamVersion、reconciliationId、rosterVersion、validEndTime、members。每项成员含userId、outTradeNo、orderId、orderStatus、reservedAt、paymentDeadline、payPrice、currency、payment或null，仅向拥有整个渠道的B后端开放。未进入核对返回RECONCILIATION_NOT_STARTED；终态仍可查询历史快照。此接口是受授权的后台接口，不透传给商城前端。

B持久化核对任务，逐一停止名单订单支付并查支付渠道：

- 截止前已支付：确保原PAYMENT_SUCCEEDED已可靠发布，使用queryEventResult及TradeService.query确认A已接受；不能把Broker确认或尚在MQ中的支付当作PAID_ACCEPTED，不得用未支付关闭绕过。需要重发时由原Outbox以相同eventId执行，不另开HTTP/RPC结算旁路。
- 确认从未开放支付/已关单未支付：调用TradeService.close，记录确认结果。
- 截止后已收款：A不计入成团；B记录退款任务，并在核对结果中明确PAID_OUTSIDE_WINDOW，不冒充CLOSED_UNPAID。
- 渠道状态未知、B订单缺失、金额/身份冲突：保留待处理并告警，不提交伪造的完整核对结果；允许团队持续RECONCILING。

`TeamService.completeReconciliation` 入参source、channel、teamId、reconciliationId、rosterVersion、completionId（稳定64字符ID）、checkedAt、results。results必须精确覆盖冻结名单且无重复，逐项为userId、outTradeNo、disposition以及证据字段：

| disposition | 必须携带 | A的校验及处理 |
|---|---|---|
| PAID_ACCEPTED | paymentId | 参团已PAID且支付事实ID一致，否则拒绝 |
| CLOSED_UNPAID | closeId | 已CANCELLED且有匹配关闭记录，否则拒绝 |
| PAID_OUTSIDE_WINDOW | paymentId,paidAt,paidAmount,currency,refundId | 真实支付时间不在允许区间，参团不得已PAID；B已有持久退款任务，A记录异常事实/退款关联并关闭剩余占位一次 |

任何UNKNOWN不得作为结果提交。整批核验及终态转换在一个A本地事务中完成；统一锁顺序，先按稳定顺序锁业务键/明细再锁团队，避免与单笔支付形成死锁。冲突允许事务回滚后重试，不部分终结成员。收到已COMPLETED团队时返回现有终态而不执行失败转换；同completionId异参返回RECONCILIATION_CONFLICT。名单不全/版本错误/成员事实未确认返回RECONCILIATION_INCOMPLETE或RECONCILIATION_CONFLICT并保留核对状态。

出参teamId、reconciliationId、teamStatus、teamVersion、resolvedAt。核对完成后已支付人数达到目标则COMPLETED，否则FAILED；PAID参团保留支付事实，失败退款另记。符合条件的最后一笔支付也可在核对期间直接触发COMPLETED，之后到达的核对请求不反转它。没有完成核对不得仅凭时间将不足人数团队判失败。

TEAM_FAILED事件outTradeNoList为冻结名单全部订单，增加reconciliationId、reason=TARGET_NOT_REACHED及refundRequiredOutTradeNoList（已被A接受支付的单号）。PAID_OUTSIDE_WINDOW退款已由B创建，不在该退款名单重复建任务。B按原订单唯一退款业务键去重，事件丢失通过TeamService.query与queryReconciliation补偿。

### 8.4 退款状态：REFUND_STATUS_CHANGED（B→A）

团队失败退款由B发起资金退款；A记录结果供核对，不执行资金操作。信封含source/channel，payload含userId、outTradeNo、paymentId、refundId、refundAmount、currency、refundStatus（PENDING/FAILED/SUCCEEDED）、refundVersion（单调整数字符串）、occurredAt、failureCode?。首版仅全额退款，refundAmount等于该订单真实已收金额。

A处理结果result含outTradeNo、refundId、refundStatus、refundVersion、recordVersion。首次以订单/支付事实绑定稳定refundId；重试失败使用同一退款业务标识，支付渠道尝试编号由B内部管理。A仅接受FAILED团队已支付订单、核对记录中的超时支付异常，或已CANCELLED记录关联的迟到付款退款；不得将COMPLETED团队普通退款作为此协议自动接收。无退款时TradeService.query返回refund=null；PENDING/FAILED可向更高版本PENDING、FAILED或SUCCEEDED推进，SUCCEEDED终态不回退。退款消息先于核对证据到达时持久等待依赖，核对完成后自动恢复，不永久拒绝合法的先到消息。

同版本同内容幂等，同版本异内容拒绝REFUND_CONFLICT；低版本忽略并记录当前状态的处理结果；SUCCEEDED不可退回FAILED/PENDING，退款金额/币种/支付归属不符同样REFUND_CONFLICT。已取消订单的迟到收款若A此前未保存支付事实，首次通知须额外携带paidAt、paidAmount并保存独立异常支付记录，不能恢复参团。B退款提交与待同步记录同事务，A暂时不可用不阻塞真实退款。

B在团队FAILED确认、所有付款均已核对且确定不会履约后即可释放尚占用库存一次，退款可能仍PENDING或FAILED；两者各自可观测，不因退款重试反复释放。正常COMPLETED转已售/待履约，任何异常支付或资金未确认订单均先阻断履约并告警。

### 8.5 状态转换摘要

| 所有者/状态 | 允许转换 | 触发 |
|---|---|---|
| A参团RESERVED | PAID / CANCELLED | 合法支付事实 / 确定关闭 |
| A参团PAID | 保持PAID，独立记录退款状态 | 不允许主动退团；失败后记录退款 |
| A团队FORMING | COMPLETED / RECONCILING | 目标人数达成 / 到期 |
| A团队RECONCILING | COMPLETED / FAILED | 接齐合法支付达标 / 完整核对后人数不足 |
| A团队COMPLETED、FAILED | 不变 | 只允许幂等读取与事件重投 |
| B商城待占位 | 待支付 / 关闭 / 保持待确认 | A占位成功 / 确定关闭 / 结果未知 |
| B已支付待成团 | 待履约 / 退款处理中 / 支付异常待核对 | 成团且本地资金匹配 / 失败 / 事实冲突 |

仅A截止时间和真实支付事实决定是否满足支付时间窗口，B不能通过重试修改paidAt。核对可以长时间等待，运营必须对RECONCILING积压告警；没有业务裁决不得强制伪造终态。

## 9. 调用与消息示例

RPC调用形态（接口已保存在group-buy-market-contract；以下省略连接与签名附件配置，完整可执行示例见docs/tests/ab-v2-client）：

```java
RpcResult<Reservation> result = tradeService.reserve(reserveRequest);
// 鉴权附件由Consumer Filter生成，业务参数不携带notifyUrl。
// 0000才是成功；超时保留原outTradeNo查单，不新建订单。
```

B支付事件：Topic=`B_TRADE_EVENTS-dev`，Tag=`PAYMENT_SUCCEEDED`，Key=`evt-b-pay-001`。签名在消息属性内，正文如下：

```json
{"eventId":"evt-b-pay-001","eventType":"PAYMENT_SUCCEEDED","schemaVersion":1,"producer":"zj-market","occurredAt":"2026-10-02T04:00:00.123Z","source":"s01","channel":"c01","correlationId":"req-001","payload":{"userId":"u1","outTradeNo":"B20261002001","paymentId":"pay-001","paidAmount":"80.00","currency":"CNY","paidAt":"2026-10-02T04:00:00.123Z"}}
```

A在占位存在且事实合法时，业务与结果事件同事务提交，发布到`A_INTEGRATION_RESULTS-dev`：

```json
{"eventId":"evt-a-result-001","eventType":"EVENT_PROCESSING_RESULT","schemaVersion":1,"producer":"group-buy-market","occurredAt":"2026-10-02T04:00:01.000Z","source":"s01","channel":"c01","correlationId":"evt-b-pay-001","payload":{"originalEventId":"evt-b-pay-001","originalEventType":"PAYMENT_SUCCEEDED","outcome":"APPLIED","code":"0000","info":"成功","processedAt":"2026-10-02T04:00:01.000Z","result":{"orderId":"A001","outTradeNo":"B20261002001","teamId":"T001","payment":{"paymentId":"pay-001","paidAmount":"80.00","currency":"CNY","paidAt":"2026-10-02T04:00:00.123Z"},"orderStatus":"PAID","teamStatus":"FORMING","recordVersion":"2","teamVersion":"3"}}}
```

若另一成员先触发TEAM_COMPLETED到达B，该支付处理结果中的旧FORMING不能回退团队状态。B仍逐单验证本地真实支付和履约幂等。

## 10. 迁移、验收与实施顺序

1. **兼容验证**：固定JDK、Spring Boot、Dubbo、Triple序列化、RocketMQ Broker/Client版本组合。构建独立契约包，直连验证trial以及认证/异常，再验证MQ收发和权限。前端继续HTTP，不需要对浏览器暴露Dubbo。
2. **基础可靠性**：A/B各自Outbox、Inbox、待依赖及隔离记录；日志关联requestId/eventId/业务键，监控发布积压、消费延迟、死信、业务拒绝、签名失败和团队核对时长。Broker用持久卷部署；本地单实例适合学习，不声称具备生产高可用。
3. **商品与占位**：先商品版本事件，再RPC试算、占位、查单、关闭。占位价格/期限/资格和B库存事务不改。商品乱序、同版本异内容、响应丢失后查单必须覆盖。
4. **支付与成团**：支付事实、结果事件、成团/失败/核对事件，最后退款同步和完整核对提交；业务消费者调用A应用服务，不通过HTTP绕回A自身。技术替换不能破坏原本支付/人数/团队/通知的事务边界。
5. **旧HTTP隔离**：旧v1接口受保护且只用于历史兼容；新订单不同时调用RPC和HTTP结算，也不同时通过HTTP/MQ发同一生命周期事件。旧notify_task维持旧HTTP投递及最多5次规则；切换前停止不兼容旧工作线程，每个历史任务明确transport和接收方。若迁移为MQ，持久分配稳定eventId、映射授权渠道、事务标记迁移完成，再启用新发布器；不能依据发送时间临时造ID。新事件不依赖notifyUrl或HTTP200/success。
6. **数据迁移**：扩容短ID、增加支付完整事实/版本/关闭标记/事件身份及可靠任务表，先审计旧数据。不凭默认值填造历史事实；不能核验的旧单仍LEGACY_FACT_UNVERIFIABLE。65项回归继续运行，原HTTP测试作为旧适配器回归，不误当MQ已验收。
7. **新增真实验证**：跨进程Dubbo及签名向量、凭据/渠道越权、超时后提交、retries=0；真实Broker收发/重启/发送确认丢失、业务提交后消费ACK丢失、同ID重复/异正文、不同ID同支付事实、乱序版本、死信和原ID重放；退款先于核对落库、成团先于支付结果、结果事件丢失后的查单恢复。
8. **竞争与恢复**：占位/取消、支付/关闭、成团/到期、完整名单核对；检查库存、人数、退款和事件不变量。未知资金状态不得最终失败；Broker不可用期间支付事实仍落B本地事务并恢复投递。
9. 机器契约改为Java接口/DTO及消息JSON Schema，附可执行签名与消息样例；OpenAPI只用于B对前端及历史HTTP接口。发布前验证新旧契约包兼容、未知schema隔离、密钥轮换和回滚路径。A实现、Java契约包、消息Schema及本机传输已验，详见[交付报告](phase5-ab-v2.md)；实际B与生产部署尚未验收。

## 11. 与1.0的变更对照

| 1.0 HTTP约定 | 1.1替代 |
|---|---|
| trial/reserve/query/team/close/reconciliation REST路径 | 第4节Dubbo方法 |
| catalog/upsert | PRODUCT_SNAPSHOT_CHANGED |
| trade/confirm-payment及同步回执 | PAYMENT_SUCCEEDED + EVENT_PROCESSING_RESULT + 查询恢复 |
| trade/refund-result | REFUND_STATUS_CHANGED + 结果事件 |
| 登记HTTP回调、200/success确认 | Topic/消费组、事务后消费确认、Inbox/Outbox |
| HTTP方法/路径/正文签名 | RPC接口/方法/DTO签名；MQ独立消息签名与Broker授权 |
| HTTP状态码和Retry-After | 业务code、RPC异常、retryAfterMillis；MQ结果/重试/隔离 |

商品价格版本、未支付关闭、到期核对、支付后不主动退团、失败退款以及库存归B的业务决策没有改变。

官方机制核对（2026-10-02）：[Dubbo协议概述](https://dubbo.apache.org/en/overview/mannual/java-sdk/reference-manual/protocol/overview/)、[RocketMQ消费重试](https://rocketmq.apache.org/docs/featureBehavior/10consumerretrypolicy/)、[RocketMQ事务消息边界](https://rocketmq.apache.org/docs/featureBehavior/04transactionmessage/)。它们用于核对协议/重试能力，不代表当前工程版本已完成兼容验证。

## 11. A侧交付资料（2026-10-03）

Java接口/DTO见 `group-buy-market-contract`；[消息Schema](schemas/ab-v2-event-v1.schema.json)、[七类公开样例](schemas/ab-v2-event-v1.examples.json)、[处理结果九类分支样例](schemas/ab-v2-processing-result.examples.json)、[非法结果回归样例](schemas/ab-v2-processing-result.invalid-examples.json)、[签名向量](ab-v2-signing-vector.md)、[启动/TLS/轮换/恢复说明](dev-ops/ab-v2/README.md)、[验收和版本矩阵](phase5-ab-v2.md)均已保存。Schema约束结构；版本数值上限、状态、支付归属和异常退款证据仍由运行时业务校验。实际B端Outbox/Inbox、资金/库存闭环及生产故障演练未完成，不因A侧验收提前宣称商城上线条件满足。

## 12. 契约收尾验收（2026-10-03，已完成）

本项仅收尾A侧结构契约、金额/字段和进度说明。Schema按原事件类型与outcome校验三类完整结果，缺失/未知字段、错误类型、成功码与拒绝结果错配均拒绝；支付嵌套事实沿用正金额、CNY及UTC毫秒格式。正则严格拒绝末尾换行，数值版本限制至Java Long上限。生成器支持--check，定向验收自动检查保存Schema与生成器一致。

最终七类公开样例、九个合法结果分支、216个非法变体、两个契约示例及17条真实Outbox通过。字段/状态/错误码说明已对照当前DTO和实现，预留行为不冒充已实现；完整B联调、生产TLS和故障演练仍见交付报告。未改交易Java代码、执行迁移或重跑无关Java回归。

## 13. 复杂度评估

本次补修属于中等契约维护复杂度：三类原事件×三种outcome形成固定九个分支，支付额外校验一层payment；以共享定义和条件Schema复用字段约束，未新增业务状态、数据库表或RPC。单消息校验成本随正文长度及字段/名单数量增长，受256KiB正文和100项名单上限约束；精确业务归属与状态核验仍由运行时执行。项目整体复杂度较高，主要来自跨服务事实、事务锁序和Inbox/Outbox恢复，而非这次结构约束。后续新增结果字段必须同步DTO/生产输出、生成器、样例及Schema专项验收。
