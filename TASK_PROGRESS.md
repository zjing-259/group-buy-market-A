# 项目 A 工作进度

更新时间：2026-10-03，Asia/Shanghai。第1项“契约收尾”已完成（当前证据见第8节）；已按用户恢复指令核对旧快照、Git状态、源码与报告，从未完成步骤继续。A侧开发与本机验收已补齐；实际B全流程和生产部署仍待验收。现有源码保留在工作区，未提交、未撤销。

## 1. 原始目标、需求和约束

依据 `docs/ab-api-contract.md` v2 1.1 与 `A-TODU.md` 的 API-001～008、FEAT-001～004，完成项目 A 对项目 B 的改造。A 使用 Java 8 / Spring Boot 2.7.12；B 目标为 Java 17 / Spring Boot 4.1.1。提供 Java 8 契约包、四个 Dubbo 3 Triple 接口，并以 RocketMQ 4.9.7 交换普通事件。

范围包含商品版本同步、资格与价格试算、占位/查单/关单、团队查询、支付事实、到期核对、退款和事件处理结果，以及签名认证、权限隔离、Inbox/Outbox、乱序等待、重试恢复。保留四阶段 Bug 修复与 v1 历史记录，不编造历史支付事实。业务约束：商品由 B 同步；终态失败前核对；已付款不能自愿退出；严格资格；最低实付 0.01 元，两位小数四舍五入，非法负值和超原价配置拒绝。

用户授权创建 Docker MQ/Dubbo 开发环境；Dubbo Provider 随 A 应用运行。用户已配置“Java JDK8”入站规则，跨主机可达性尚未验证。不得未经确认提交代码，不得重置已有修改、输出本地密钥或重复执行非幂等迁移。

## 2. 已完成且已保存的文件与改动

### 2.1 本轮继续工作前已有的实现

| 文件/目录 | 已保存的改动 |
| --- | --- |
| `pom.xml`、`group-buy-market-contract/pom.xml`、`group-buy-market-contract/src/main/java/cn/bugstack/api/v2/` | Java 8 契约模块、DTO、统一结果/错误码、认证异常及 Marketing/Trade/Team/Integration 四个接口；Dubbo 3.2.16、MQ client/ACL 4.9.7。生成器为 `docs/dev-ops/ab-v2/generate-contract.py`。 |
| `group-buy-market-app/pom.xml`、`Application.java`、`application-dev.yml`、`Dockerfile` | 接入契约/RPC/MQ；应用传递启动参数；Triple 50051、直连；v2 默认关闭；Java 8 镜像和 JVM 参数修正。 |
| `group-buy-market-app/src/main/java/cn/bugstack/v2/`、`src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter` | v2 业务引擎、RPC Provider、鉴权、事件/等待任务、Inbox/Outbox、调度与 MQ；规范 JSON 哈希；历史业务键冲突拒绝不可核验事实；取得团队锁后重读数据库时间。 |
| `V2LegacyHttpGuard.java` | 旧 HTTP/DCC 默认要求 `X-Legacy-Token`；v2 开启后旧新锁单默认 410，兼容开关显式配置。 |
| `docs/dev-ops/mysql/sql/migrations/20261002-ab-v2.sql`、`20261002-ab-v2-waiting-key.sql`、`docs/tests/run-phase1.ps1` | 商品/用户 ID 扩容、10 张 v2 表、等待键改为 consumer/producer/event_id；测试库复制清单补齐。开发库主迁移已执行，隔离库v2表/等待键已存在；隔离库共享列容量的旧快照错误已于10月3日核对修正，见第3节。 |
| `docs/dev-ops/ab-v2/`、`.gitignore`、`docs/ab-v2-signing-vector.md` | Docker Compose、Broker、启动脚本、四个开发 Topic、受忽略的本地凭据与公开签名向量。 |
| `A-TODU.md`、`docs/ab-api-contract.md` | A侧实现/测试及契约收尾状态已统一；实际B与生产验收待完成。 |

四阶段旧修改仍保存在 domain/infrastructure/trigger/api/types/app 模块、Mapper、SQL 及 `docs/phase1-bugfix.md`～`phase4-bugfix.md`；包括原有 `SCRuleFilter.java` 删除，均未重置。金额策略、锁单/支付、可靠通知租约、严格资格与用户精确隔离等旧修改应继续保留。完整文件状态以 `git status --short` 为准，新增目录仍为未跟踪状态，不代表内容未保存。

### 2.2 最近继续工作新增或完善的实现

下表中 Java 生产文件均在 `group-buy-market-app/src/main/java/cn/bugstack/v2/`，测试在 `group-buy-market-app/src/test/java/cn/bugstack/test/v2/`。

| 文件 | 具体改动 |
| --- | --- |
| `V2PaymentIdentity.java`（新增）、`V2PaymentEngine.java` | 普通/异常支付共用 paymentId 与 source/channel/外部订单绑定；支付身份核验先于到期核对副作用。复用已有表，本轮无新迁移。 |
| `V2EventEngine.java` | Inbox 身份写入后设置事务保存点，拒绝/等待/非法消息回滚业务副作用；并发同 eventId 幂等；严格重复 JSON 键和未知字段；退款时间校验、取消订单真实支付退款、异常证据冲突检查、成团后普通退款明确拒绝、成功退款版本不降级。最后补了信封/负载标量类型和 correlationId 校验，禁止数字 ID/字符串版本被隐式转换；商品/退款哈希基于规范 DTO，省略与 null 可选字段一致。 |
| `V2TeamEngine.java` | 保存首次成团完成 ID/哈希用于冲突重试；拒绝未来支付事实；异常证据一致性校验、支付身份复用，避免覆盖原证据。 |
| `V2TradeEngine.java` | 用户/活动配额锁串行化；计入同一精确用户的 v1/v2 记录，防跨 SKU 并发绕过限额。 |
| `V2MarketingEngine.java` | 空请求、渠道黑名单、活动状态/数据库时间、严格资格及依赖异常处理补齐。 |
| `V2VerificationKeys.java`（新增）、`V2Security.java` | 严格解析保留密钥 JSON，RPC 支持当前与保留密钥验证：`ab.v2.rpc.verification-keys`。 |
| `V2MqTransport.java` | MQ 保留密钥：`ab.v2.mq.b-verification-keys`；验签后约束 Topic/类型/client/source/channel；256KiB 限制；未知密钥隔离与失败重试；最大重消费 8，1/2/4/8 分钟退避；TLS 配置和默认要求 TLS，开发环境显式例外。 |
| `V2TlsTransporter.java`（新增）、`META-INF/dubbo/org.apache.dubbo.remoting.api.pu.PortUnificationTransporter`、`application-v2-tls.yml`（新增） | TLS 端口在协议解码前安装首包检查，拒绝明文；原生 Netty TLS/mTLS 验证证书。配置读取 AB_V2_TLS_SERVER_CERT/KEY/CLIENT_CA。实现依赖 Dubbo 3.2.16 的 NettyChannel 反射访问，升级需要回归。尝试过的 WireProtocol 包装器已删除，未保留该废弃实现或其旧生成资源。 |
| `V2PaymentFlowTest`、`V2ProductEventTest` | 扩充为 18/8 项，覆盖并发配额、关单/支付、等待重试、退款乱序、核对事务回滚、异常证据、未来时间、重复完成、严格消息类型等。 |
| 新增 `V2MarketingContractTest`、`V2AuthorizationTest`、`V2OutboxRecoveryTest`、`V2TlsGateTest` | 分别 4/7/3/2 项；覆盖资格时间/权限密钥重放/租约回收及锁等待跨租约/TLS 明文拒绝。原 LegacyHttpGuard、SecurityVector 各 1 项。 |
| `V2DubboSmokeTest`、`docs/tests/run-ab-v2-smoke.ps1`（新增脚本） | 实际 TLS Triple 与 MQ 冒烟；脚本启动隔离库临时 Java 8 A，支持独立客户端与 TLS，最终停止自己启动的进程并恢复环境。 |
| `docs/tests/ab-v2-client/pom.xml`、`CreateTestCertificate.java`、`Boot4ClientTest.java`、`MqDeadLetterTest.java`（新增） | 独立 Boot 4.1.1、release 17 客户端夹具，使用原始 Dubbo/MQ SDK；Netty 固定 4.1.118.Final；生成 7 天本地测试 PEM，真实签名 RPC/MQ、明文拒绝和专用加速死信测试。最近又增加签名 trial 并重构 ReferenceConfig，该编辑已编译并通过真实TLS脚本；又补充八个签名RPC方法及占位/支付/核对/退款完整链路。不是实际 B 改造。 |
| `docs/dev-ops/ab-v2/broker.conf`、`start-dev.ps1`、受忽略 `.local/plain_acl.yml` | Broker 延迟档位已生效；添加独立死信测试组/权限。最后将正常消费组自身权限补为 PUB\|SUB 以允许重试写回，业务 Topic 权限不变；最后权限编辑已现场验证，B仍不能发布A业务Topic。 |
| `docs/dev-ops/ab-v2/generate-event-schema.py`、`docs/schemas/ab-v2-event-v1.schema.json`（新增） | 生成七类事件的 draft2020-12 JSON Schema，限制字段/类型/格式；生成成功，已正式校验七类样例、52非法变体、2契约示例及17条实际Outbox；新增公开examples.json与validate-ab-v2-schema.py。Schema补正金额、活动ID格式及名单唯一性。 |

## 3. 环境、保存状态与已解决的中间问题

- 开发库 `group_buy_market`、隔离库 `group_buy_market_phase1_test` 的v2表和等待键已存在。10月3日实际核对发现其sc_sku_activity.goods_id与crowd_tags_detail.user_id仍16字符，开发库均64；仅将隔离库两列定向补齐为64。run-phase1.ps1增加容量检查后再按需扩容；没有重跑整份迁移。**不要再执行已完成的非幂等迁移**。测试库数据会清理，不能用于稳定演示。
- Docker MySQL/Redis、MQ NameServer/Broker 最近已运行；Broker 因延迟档位调整重启过。恢复时只读检查实际状态。四个 Topic：B_PRODUCT_EVENTS-dev、B_TRADE_EVENTS-dev、A_TEAM_EVENTS-dev、A_INTEGRATION_RESULTS-dev。
- 本地凭据/证书/日志在 `docs/dev-ops/ab-v2/.local/`，已忽略，不输出、不提交。签名向量中的公开密钥是测试数据。Broker 广播 127.0.0.1，当前适合宿主机客户端，容器客户端另需网络方案。
- 临时 A 进程已停止，最近检查无 Java 进程。A 镜像 `group-buy-market:v2-dev` 曾构建但未部署，且早于最新源码，**镜像已过时**。最新根项目 JAR 已打包。
- 契约模块已安装到 `C:/Users/wanghao/.m2/repository`，独立客户端可复用。实际使用现有 JDK 21 运行 release 17 客户端，精确 JDK 17 运行时未验。
- 至今未修改实际 `D:/Java/zj-market`，只读取其 POM。未进行任何 Git 提交。
- 已解决中间失败：WireProtocol SPI 包装器映射异常、仅环境变量未启用 SSL、Boot 4 默认 Netty 4.2 与固定 Dubbo TLS 不兼容（已固定 4.1.118）、死信观察器共用组导致订阅冲突（已分组）。不要重新当作待修问题。

## 4. 已运行测试、结果和准确边界

| 验证 | 最近结果 | 边界 |
| --- | --- | --- |
| 全模块回归/打包，`.local/final-all-validation.log` | 2026-10-02 23:43:33，108/108，无失败/错误/跳过，根项目所有模块 SUCCESS。含旧 65 + 当时 A v2 43。 | 早于最后消息标量校验与新增数字 ID 回归。 |
| 最后 A 专项及根项目打包，`.local/final-v2-validation.log` | 2026-10-02 23:50:54，44/44，无失败/错误/跳过，BUILD SUCCESS；进程退出码 0 已收取。 | **覆盖最新生产源码**；旧 65 未因最后这次定向补修重复跑。 |
| A 专项分项 | PaymentFlow18、ProductEvent8、Authorization7、OutboxRecovery3、Marketing4、LegacyGuard1、Vector1、TlsGate2，合计44。 | 隔离 MySQL/本地依赖测试，不代替实际 B 全流程。 |
| 最近实际 TLS/mTLS Triple/MQ 脚本 | A Dubbo2、MQ2 均通过，临时 Provider 正常退出；B 凭据发布 A 团队 Topic 被 ACL 拒绝。 | 2026-10-03最终重跑已覆盖最后编辑及新增真实交易测试，A2+2通过。 |
| 独立 Boot4 客户端 | Boot4ClientTest 5/5，MqDeadLetterTest 1/1，无失败/错误/跳过。TLS 明文拒绝、签名 RPC/商品 MQ 与结果查询、有限重试进入死信通过。 | JDK21/release17；最新客户端代码已通过；八个签名RPC方法、占位/支付/核对/退款及用户精确隔离有现场证据。死信是专用 max1 加速组，不是正常业务组完整8次策略。 |
| Outbox 恢复 | 旧令牌不能更新、过期租约重领复用事件体/ID、等待行锁跨租约拒绝结果、超过5次仍保留事实等通过。 | 未做长时故障、真实进程杀死或 Broker ACK 丢失注入。 |
| 旧 HTTP、本地构建 | 先前实测无令牌403、v2旧锁单410；旧镜像构建成功；此前 git diff --check 通过。 | 镜像过时；最后客户端/ACL/本文编辑后未再次检查全部差异。 |
| Schema | 生成器成功运行。 | 本地忽略目录安装jsonschema4.25.1+rfc3339-validator0.1.4；七类样例、52非法变体、2契约示例、可选字段省略/null和17条真实Outbox通过。Schema不替代业务状态/归属/数值上限校验。 |

2026-10-03恢复时只运行必要现场验证，未重跑旧65/完整108。新增真实链路最初因隔离库短字段失败，已核对并补齐；随机优惠ID改为旧实体支持的数字格式。最终A现场4项、Boot4客户端5项及死信1项均通过。临时A进程停止；Schema验证与PowerShell准备脚本语法检查通过；git diff --check通过（仅行尾提示）。

新增交付资料：`docs/phase5-ab-v2.md`、`docs/schemas/ab-v2-event-v1.examples.json`、`docs/tests/validate-ab-v2-schema.py`；更新README的TLS/轮换/恢复说明、契约实施状态及清单API/FEAT的A侧验收状态，未提前关闭整体B联调与发布门槛。真实交易夹具只在隔离库创建唯一活动，清理活动/优惠/映射，保留v2事实供复核；加速该团队截止后由正常A调度进入核对。

## 5. 尚未完成、未解决问题与下一步

1. **实际B接入与跨主机**：独立夹具通过不等于实际B完成。实际B资金核验、库存释放、退款/履约、Outbox/Inbox及核对任务恢复待联调；JDK21/release17已验，精确JDK17未验；Java JDK8入站规则未跨主机验。
2. **生产安全/部署**：Triple临时证书TLS/mTLS通过，正式证书生命周期/主机名策略及生产部署未验；MQ代码有TLS配置和默认保护，但实际Broker TLS未验。开发回环明文例外不得复制生产。固定Dubbo/Netty与反射TLS gate升级需回归；旧A镜像过时未部署，届时重建并规划Broker可达地址。
3. **完整故障恢复矩阵**：长时MQ/Redis故障、Broker ACK丢失、消费者杀进程、多实例、提交后响应丢失、正常业务组8次重试、受控业务死信回放和B任务恢复待演练。专用max1死信与数据库租约验证不代替这些场景。
4. **发布/切换**：A实现和本机证据已同步交付报告/README/清单，但API/FEAT上线验收不能提前全关闭。旧HTTP令牌、旧工作器停止与切换协调待部署。历史notify_task保留HTTP5次；不造历史MQ事实。FEAT-005操作台未实现且不在本轮范围。
5. **已发现限制**：旧GroupBuyDiscount.discountId为Integer，字符串非数字会导致旧Mapper读取失败，测试保持数字格式；该内部字段不属于v2对外契约，本轮未扩展其类型。隔离库共享列容量缺口已修复，不是仍待处理问题。Schema正式结构校验已完成，业务状态/支付归属/数值上限继续由运行时校验。

## 6. 下一次会话恢复说明

先读本文、交付报告与清单，再核对Git、源码和Surefire报告。A最新生产源码44项及打包、最新TLS现场10项和Schema校验已有通过证据；没有新增代码或失败时，不重复这些测试。不得重复迁移、重建环境或输出.local凭据。

从第5节实际B/跨主机和生产故障验收继续。真实SDK调用A的试算、占位、关闭、查单、团队、核对、支付MQ和退款MQ已有证据，勿重写已完成链路。实际B适配前核对共享DTO及固定SDK/Netty依赖，不复制A的Boot2 Starter到Boot4。

如出现新变更或环境问题，定向重跑命令：

```powershell
./docs/tests/run-ab-v2-smoke.ps1 -MavenRepository 'C:/Users/wanghao/.m2/repository' -ClientJavaHome 'C:/Program Files/Java/jdk-21' -Tls
python docs/tests/validate-ab-v2-schema.py --database
```

脚本使用隔离库并停止自己的临时Provider。现有开发服务最近已恢复，恢复时只读检查；所有代码未提交，保留现有旧阶段与v2修改。

## 7. 处理结果Schema复审补修（2026-10-03）

用户指出result任意对象仍能通过，此次仅修复契约与文档：generate-event-schema.py增加支付/商品/退款结构定义、outcome条件和嵌套payment约束；result始终必填，成功/忽略为完整对象且成功码，拒绝为显式null且非成功码。修正七类公开样例的不完整支付result，新增ab-v2-processing-result.examples.json（九分支）和ab-v2-processing-result.invalid-examples.json（52个保存的非法样例），扩充validate-ab-v2-schema.py。最终九分支、七类样例、146非法变体、两个契约示例及17条真实Outbox通过。原Schema验收记录按历史保留，不代表当时已验证结果内部结构。

契约与清单统一最低实付0.01元、不允许零元购，优惠额可0.00；清除当前“尚未创建接口”等过时说明，保留已标记的历史Bug证据。契约、清单、报告、README与本文末尾补复杂度评估。未修改Java交易代码、数据库、提交代码或重复跑既有Java套件；后续复审从更新后的生成器与样例核对，不重新实施已完成业务。

## 8. 第1项契约收尾完成

在上一轮已完成九分支结构约束基础上，本轮核对DTO/V2Format/结果生成器和文档，补齐金额/ID/时间/版本正则严格结尾及Java Long版本上限；generate-event-schema.py新增--check，validate-ab-v2-schema.py检查生成一致性并增加空值、上下界、溢出与换行回归。最终七类样例、九结果分支、216非法变体、两个契约示例、17条现有Outbox通过。

A-TODU第1项已勾选完成，API-007当前RPC/MQ字段/响应替换过时HTTP表述；契约可查询状态改为实际WAITING_DEPENDENCY/PROCESSED，预留限流/依赖超时提示不列为已实现。金额/字段/进度说明统一；交付报告记录当前验收。保留早期52/146计数为历史证据，当前为216。未改生产Java、数据库或提交代码，不重跑旧65/44或现场10项。下一步仍由用户选择B接入/生产与故障演练，本轮不继续这些阶段。

## 9. 复杂度评估

本次补修复杂度中等：固定三类原事件×三种outcome，支付再嵌套一层事实，维护生成器、样例和文档即可；未增加运行时表/接口/业务状态。整体A/B状态机与故障恢复复杂度较高，实际B和生产演练继续按第5节推进。
