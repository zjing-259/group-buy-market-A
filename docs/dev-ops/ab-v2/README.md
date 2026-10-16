# A/B v2 本地联调

## TD-01 新发现能力（2026-10-14）

`TeamDiscoveryService.list`已实现并通过独立MySQL／Redis／真实TCP TLS验收。契约1.1.0-td.1已安装本机Maven，SHA见 [候选清单](../../../../zj-market/docs/team-discovery-a-release.json)；B依赖／HTTP／页面及原B06镜像未升级。目标A库需先使用维护权限一次性执行 `../mysql/sql/migrations/20261014-team-discovery.sql`，核对两个新增索引；新查询显式依赖这些索引，运行账号不授DDL。

隔离复现先在A构建普通包及测试类：

```powershell
mvn -B -o -pl group-buy-market-app -am '-DskipTests=false' '-DfailIfNoTests=false' '-Dtest=V2TeamDiscoveryCursorTest,V2AuthorizationTest,V2TlsGateTest,V2SecurityVectorTest' package
python -B docs/tests/run-team-discovery.py --run-name td01_20261014_05
```

必须从A仓库根目录执行，批次名称不可复用。需要已有Docker Desktop desktop-linux和自有zj-market-b06 MySQL、原工作树私有MySQL信任材料、已缓存Java8／Redis镜像及JDK21证书工具；只创建新TD01库、专用SSL运行账号和自有临时容器／网络，不停止／重建原容器或复制历史业务数据。密码／测试证书仅留忽略的 `.local/<批次>` 受限ACL，报告与失败批次留A的 `target/team-discovery/<批次>`；临时资源按td01.owner归属清理，隔离库保留。

最终04批69项通过，执行计划使用新双索引；20个SQL样本p95=0.171ms、20次串行RPC p95=15ms。合成候选计数和直接事件引擎付款兼容测试不能代替B资金／MQ／前端验收。测试使用生产beans的显式装配，不启动调度器／MQ工作器；普通Boot整栈／正式容器发布留TD05。过程及失败记录见 [功能文档](../../../../zj-market/docs/team-discovery-development.md#14-td-01-实施与验收2026-10-14已完成)，旧联调脚本／以下记录保持原范围。

当前脚本用于 **A、B Java 进程在 Windows 宿主机运行**、RocketMQ 在 Docker Desktop 运行的开发环境。Dubbo Triple Provider 随 A 进程启动，监听 50051；Dubbo 没有独立 Broker 容器。`broker.conf` 公布 `127.0.0.1`，MQ 端口只绑定宿主机回环。若改为容器化 A/B，需先调整 Broker 公布地址与端口可达性并重新做收发测试。

1. 在项目根目录完成 Maven 打包。首次数据库迁移 `docs/dev-ops/mysql/sql/migrations/20261002-ab-v2.sql` **只能应用一次**；请先核对已有库结构，勿重复执行其 `ALTER`。
2. 运行 `./docs/dev-ops/ab-v2/start-dev.ps1`。脚本复用或生成 `.local/secrets.json`，启动 RocketMQ NameServer/Broker，创建四个契约 Topic，并生成 `.local/app.env`、`.local/b.env`。`.local/` 已忽略，勿提交或打印密钥。
3. 启动 A 时把 `.local/app.env` 注入进程环境，并配置 MySQL、Redis；仅在完成隔离库验证后才接开发库。`AB_V2_ENABLED=true` 才启用 v2 Provider/MQ；旧 HTTP 入口默认需要 `X-Legacy-Token`，令牌值见 A/B 的本地环境文件。v2 启用时旧 HTTP 新锁单默认返回 410，正式切换不要开启 `AB_V2_LEGACY_NEW_ORDERS_ENABLED`。
4. B 侧使用 `.local/b.env` 中的开发凭据。Dubbo 直连 `tri://<A地址>:50051`，服务 group=`gbm-dev`、version=`2.0.0`，并按契约传认证附件。跨机器访问时仍需检查实际 50051 网络可达；本机测试不能证明外部入站规则有效。

RPC 规范序列化及 HMAC 的公开测试向量见 [`docs/ab-v2-signing-vector.md`](../../ab-v2-signing-vector.md)。

2026-10-03 已复验实际 TLS/mTLS Triple、签名试算/事件结果查询、MQ 商品消费/结果 Outbox、B 禁止发布 A Topic，以及专用测试组有限重试进入死信。最新 A 专项44项通过；此前旧65项及当时v2专项43项联合108项通过。独立客户端是测试夹具，实际 B 接收端和生产部署仍待联调。详见 [交付与验收报告](../../phase5-ab-v2.md) 和 [进度](../../../TASK_PROGRESS.md)。已有镜像早于最新代码，部署时须重新构建。

## 定向验证

在根目录运行，复用已迁移的隔离库、已有容器及契约缓存：

```powershell
./docs/tests/run-ab-v2-smoke.ps1 -MavenRepository 'C:/Users/wanghao/.m2/repository' -ClientJavaHome 'C:/Program Files/Java/jdk-21' -Tls
python -m pip install --target docs/dev-ops/ab-v2/.local/schema-validator jsonschema==4.25.1 rfc3339-validator==0.1.4
python docs/tests/validate-ab-v2-schema.py --database
```

脚本要求50051空闲及13306/16379/9876可用，使用隔离数据库，启动并最终停止自己创建的 Java8 A 进程；日志进入被忽略的 `.local/`。TLS证书由已有JDK生成，仅用于本地7天测试。独立客户端运行于JDK21、编译release17，不能据此宣称JDK17或实际B已经验收。安装验证器仅需首次执行；Schema校验脚本读库，不改数据。Schema和七类公开样例在 `docs/schemas/`；Schema校验结构，支付归属、状态、版本数值上限及退款证据仍由业务引擎校验。

## TLS 与密钥轮换

- Triple：正常环境配置与 `v2-tls` profile 同时启用，外部注入 `AB_V2_TLS_SERVER_CERT`、`AB_V2_TLS_SERVER_KEY`、`AB_V2_TLS_CLIENT_CA`。客户端配置自己的证书/私钥和服务端信任链。服务端拒绝明文；证书不要使用本地测试密钥。TLS gate 依赖固定Dubbo实现，升级SDK需复验。
- MQ：`ab.v2.rocketmq.use-tls=true` 与客户端/Broker证书配置配套。`require-tls` 默认true；未启用TLS时不启动传输，Outbox保留。`application-dev.yml` 为回环Broker显式设false；实际Broker TLS尚未验收，不要把此例外复制到生产。
- RPC：当前 `ab.v2.rpc.key-id/secret`，保留密钥通过 `ab.v2.rpc.verification-keys` 的 JSON 对象配置。MQ接收B当前 `ab.v2.mq.b-key-id/b-secret` 和 `ab.v2.mq.b-verification-keys`；A发布使用 `a-key-id/a-secret`。JSON形如 `{"retained-key-id":"externally-injected-secret"}`，值应由外部机密配置提供。
- 轮换先让接收方保留旧/新增验签密钥，再切发送方当前密钥；旧密钥保留至消息保留和人工回放窗口结束。当前实现按启动配置读取，修改后按部署策略重启；不支持热轮换管理接口。MQ轮换重签原始Outbox正文，不改变eventId/body/hash。

## 可靠任务与切换

正常消费组最大重消费8次，延迟1/2/4/8分钟后维持8分钟；专用死信测试使用max1加速路径，不代表正常8次等待全验。消费组自身PUB|SUB用于Broker重试写回，业务Topic仍按A/B角色限制。签名、Topic与producer/client、事件类型及渠道同时校验，Tag不是授权边界。

Outbox状态PENDING/CLAIMED/PUBLISHED，60秒租约；发布失败以1/2/4/8分钟退避并持续保留。发现异常先查Outbox错误、Inbox结果、等待依赖和隔离区，再结合RPC查询核验。人工处理不得重建eventId或修改已保存正文；没有交付操作台/自动业务死信回放。需要临时暂停某个发布任务时，应在运维事务内锁定该任务并确认工作器状态，将待执行时间推迟；不要改PUBLISHED记录为未发布或绕过认领令牌。

正式切换前停止旧工作器混跑，配置旧HTTP令牌并保持旧新锁单禁用。历史notify_task继续使用旧HTTP通知机制；无法核验历史订单返回LEGACY_FACT_UNVERIFIABLE。两份20261002迁移已执行，勿重复ALTER；新部署只在审计结构与备份后按迁移顺序应用。

处理结果Schema已按originalEventType/outcome限制内部字段；APPLIED/IGNORED必须完整结果，REJECTED显式null。合法/非法结果样例与生成器在docs/schemas和docs/dev-ops/ab-v2，仍用上述validate-ab-v2-schema.py定向验收。金额政策为最低实付0.01元、不允许零元购；优惠额可0.00。

契约收尾已完成：金额/ID/时间格式拒绝末尾换行；数值版本为1～9223372036854775807。验证脚本自动执行生成器--check，检查保存Schema一致性；当前七类样例、九结果分支、216非法变体与17条真实Outbox通过。金额仍为两位字符串和最低实付0.01元，优惠额可0.00。无需为本次Schema/文档修改重跑业务套件或迁移。

## 复杂度评估

本次Schema与文档改动复杂度中等，固定九个分支与一层payment，重用现有验证器，无服务部署、数据库变更或新业务依赖。开发环境运行复杂度仍主要在证书/ACL/密钥及隔离库一致性；实际B联调和生产故障演练仍须独立验证。
