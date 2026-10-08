# group-buy-market 拼团营销服务

`group-buy-market`（项目 A）是一个 Java / Spring Boot 多模块后端，负责活动优惠、参团占位、支付事实核验、团队状态及可靠通知。项目保留 v1 HTTP 兼容接口，并实现面向商城服务 B 的 v2 Dubbo Triple / RocketMQ 链路。

A 不负责商城库存、实际收款、资金退款或履约；v2 只接收可信 B 的商品、支付与退款事实，并保存处理结果。当前支持单 SKU、数量 1、CNY；最低实付 0.01 元，不允许零元购。优惠计算使用 BigDecimal、HALF_UP 两位小数，v2 金额字段使用严格两位字符串。

本文于 **2026-10-08** 核对实际源码和部署文件。已完成的本机验收不等同于实际 B 联调或生产部署；历史测试与剩余事项见[交付报告](docs/phase5-ab-v2.md)和[维护清单](A-TODU.md)。本次文档核对未重新启动服务或执行业务测试。

## 技术栈与环境要求

版本依据根 POM、模块 POM 和 Compose；依赖管理中的其他库不表示项目已有相应业务功能。

| 技术 | 实际版本/配置 | 用途 |
| --- | --- | --- |
| Java | 源码及目标字节码 8；Docker 使用 `eclipse-temurin:8-jre` | A 应用与共享契约 |
| Spring Boot | 2.7.12 | Web、应用装配、事务与调度 |
| MyBatis Starter | 2.1.4 | Mapper / DAO 持久化 |
| MySQL / JDBC 驱动 | Compose：8.0.32；mysql-connector-java：8.0.22 | 交易、活动、Inbox/Outbox；SQL 使用 MySQL 8 排序规则 |
| Redis / Redisson | Compose：Redis 6.2；Redisson Starter：3.26.0 | 动态配置、缓存/锁、RPC nonce 防重放 |
| Dubbo | 3.2.16，Triple，直连注册配置 `N/A` | v2 同步 RPC |
| RocketMQ | Client / ACL / Docker Broker：4.9.7 | v2 事实与团队事件 |
| Fastjson / Guava | 2.0.28 / 32.1.3-jre | 序列化与通用工具 |
| OkHttp / Retrofit | OkHttp 相关依赖 3.14.9；converter-gson 2.9.0 | HTTP 通知等基础能力 |
| 测试与构建 | 主工程 JUnit 4；Surefire 2.22.2；Compiler Plugin 3.0 | 显式启用测试、Java 8 构建 |

本地准备 JDK 8、Maven、MySQL 8 和可达的 Redis；启用 v2 时还需 RocketMQ、调用方授权及签名配置。仓库未锁定 Maven 可执行版本，也未提供 Maven Wrapper，最低可用 Maven 版本**待确认**。Docker 部署需要 Docker Engine/Desktop 与 Compose；仓库联调脚本使用 PowerShell。

`docs/tests/ab-v2-client` 是独立 Boot 4.1.1、release 17 的兼容测试夹具，**不是 A 的运行环境，也不是完整 B 应用**。其已有验证运行于 JDK 21，精确 JDK 17 运行时待确认。

## 项目结构

| 模块/目录 | 职责 |
| --- | --- |
| [group-buy-market-contract](group-buy-market-contract) | Java 8 的 v2 RPC 接口、DTO、统一结果和错误码 |
| [group-buy-market-api](group-buy-market-api) | v1 HTTP 服务接口、请求/响应 DTO |
| [group-buy-market-app](group-buy-market-app) | 启动入口 `cn.bugstack.Application`、配置、测试、Dockerfile；当前 v2 Provider、业务引擎与 MQ/任务实现也位于本模块 |
| [group-buy-market-domain](group-buy-market-domain) | 活动优惠试算、规则链、锁单/结算、标签及领域端口 |
| [group-buy-market-infrastructure](group-buy-market-infrastructure) | DAO/PO、仓储实现、Redis、DCC 与 HTTP 通知适配 |
| [group-buy-market-trigger](group-buy-market-trigger) | v1 Controller、通知补偿定时任务与模拟回调入口 |
| [group-buy-market-types](group-buy-market-types) | 公共响应码、异常、注解与设计框架工具 |
| [docs](docs) | 契约、消息 Schema/样例、SQL、部署和测试脚本、阶段报告 |

依赖装配由 app 汇总 trigger 与 infrastructure，领域通过端口连接基础设施；v2 引擎当前直接使用 JdbcTemplate，不应把它描述成完全独立的纯领域模块。复杂交易、锁序和恢复设计放在[接口契约](docs/ab-api-contract.md)、[交付报告](docs/phase5-ab-v2.md)及[阶段修复报告](docs/phase4-bugfix.md)，此处只给启动与入口导航。

## 已有核心功能

- **营销试算**：渠道商品活动映射、直减/满减/折扣/N 元购、可见性与参与资格、黑名单和流量切分。v2 使用 B 同步的版本商品投影，新试算/占位校验版本及售卖状态。
- **占位与查询**：开团/参团、参与限额、业务键幂等、持久价格与期限快照；v2 支持外部订单查询、本人团队摘要和未支付关闭标记，阻止迟到占位。
- **支付与团队**：v1 支付结算；v2 校验用户、渠道、金额、币种、支付 ID 与时间，事务内记录支付和成团。到期先进入 RECONCILING，经 B 完整核对后才能确认不足人数的 FAILED。
- **退款事实**：v2 保存全额退款状态/版本，处理乱序与异常支付关联；不执行资金退款，已支付订单不能主动退团。
- **可靠通知**：v1 HTTP 通知任务认领、租约、重试和补偿；v2 Inbox/Outbox、等待依赖、隔离消息、持久处理结果及查询。重投保持 eventId/正文，Broker 成功不代表业务已应用。
- **认证配置**：v2 RPC HMAC、nonce 防重放、渠道授权、TLS/mTLS 配置；MQ HMAC、Topic/身份匹配、ACL 与保留验签密钥；旧 HTTP 默认受兼容令牌保护。

标签资格校验已存在，但 `TagService.execTagBatchJob` 的用户采集仍是固定示例列表，不能当作真实用户采集平台。模拟回调也不是 B 的生产接收端。死信操作台、自动业务死信回放及实际 B 库存/资金闭环未交付。

## 快速启动：宿主机运行 A

以下路径和命令均以项目根目录为当前目录。现有数据库先核对结构，勿为启动重复初始化。

### 1. 准备 MySQL 与 Redis

开发配置默认连接 MySQL `127.0.0.1:13306`、数据库 `group_buy_market`，Redis `127.0.0.1:16379`。

已有依赖可直接复用。新环境可以使用[环境 Compose](docs/dev-ops/docker-compose-environment.yml)的 MySQL 服务；其开发凭据应通过本地 Compose 覆盖配置调整，不沿用到生产：

```powershell
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql
```

MySQL 首次初始化会读取挂载目录 `docs/dev-ops/mysql/sql` 下的基础 SQL；子目录 `migrations` 不会由该挂载自动递归执行。已有数据库/数据目录不能据此认为迁移已应用。

**当前 Redis 挂载需先处理**：工作区的 `docs/dev-ops/redis/redis.conf` 是目录，而 Compose 将它当作配置文件挂载并执行 `redis-server`。因此不能保证该 Compose 的 Redis 服务直接启动成功。请复用已有 Redis，或在本地部署配置中准备真正的 Redis 配置文件；这份 README 不修改部署文件。当前 Redisson 单机配置的 `.setPassword(...)` 被注释，仅设置 `redis.sdk.config.password` 不会启用 Redis 认证；受认证 Redis 的接入方式待确认。

### 2. 初始化与迁移

[基础 SQL](docs/dev-ops/mysql/sql/xfg-frame-archetype.sql)创建 `group_buy_market`，包含示例数据及 `DROP TABLE`，只能用于明确的新建空环境，不能对已有业务库重放。手动初始化时，在连接到正确实例的 MySQL 客户端中，从项目根目录执行：

```sql
SOURCE docs/dev-ops/mysql/sql/xfg-frame-archetype.sql;
```

当前基础 SQL 已吸收一期字段/精确单号与通知租约结构。新库不应再整份重放以下三个旧迁移；已有旧库则按结构、索引及数据审计决定缺失操作：

| 迁移 | 内容 |
| --- | --- |
| [20260926-phase1.sql](docs/dev-ops/mysql/sql/migrations/20260926-phase1.sql) | 金额/请求快照、团队 SKU、业务键与配额守卫 |
| [20260926-phase1-reaudit.sql](docs/dev-ops/mysql/sql/migrations/20260926-phase1-reaudit.sql) | 精确外部单号、毫秒支付时间、通知地址容量 |
| [20260927-phase3-notify.sql](docs/dev-ops/mysql/sql/migrations/20260927-phase3-notify.sql) | 通知认领、租约、执行时间及错误记录 |

若是刚导入基础 SQL、尚无 v2 表的新库，启用 v2 前继续执行：

```sql
USE group_buy_market;
SOURCE docs/dev-ops/mysql/sql/migrations/20261002-ab-v2.sql;
SOURCE docs/dev-ops/mysql/sql/migrations/20261002-ab-v2-waiting-key.sql;
```

前者扩容共享商品/标签用户 ID 并创建 10 张 v2 表；后者统一等待任务键为 `(consumer, producer, event_id)`。这些迁移不是完整幂等脚本。已有库必须检查字段、表及主键后再执行，不能凭日期重放；开发库/隔离库历史记录见[工作进度](TASK_PROGRESS.md)，其他部署实例的应用状态**待确认**。

### 3. 构建与运行

```powershell
mvn -pl group-buy-market-app -am -DskipTests package
```

产物为 `group-buy-market-app/target/group-buy-market-app.jar`。主工程默认跳过测试；上述命令只构建，不能作为行为验收。Maven 仓库解析失败时检查本地 `settings.xml`/镜像配置，根 POM 的仓库声明仍包含 HTTP 地址。

启动前通过本地机密配置安全注入 `SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`；如需访问旧 HTTP，再注入 `AB_V2_LEGACY_HTTP_TOKEN`。README 不提供这些值。非敏感地址可按下列开发配置覆盖：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:13306/group_buy_market?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:REDIS_SDK_CONFIG_HOST = '127.0.0.1'
$env:REDIS_SDK_CONFIG_PORT = '16379'
java -jar group-buy-market-app/target/group-buy-market-app.jar --spring.profiles.active=dev
```

HTTP 默认端口为 `8091`。`application.yml` 默认激活 dev，`--spring.profiles.active` 可显式覆盖；test/prod 配置中的数据库和 MyBatis 块大多被注释，不能把它们视为可直接运行的完整环境。Maven 的 `-Pprod` 也不等于已准备生产运行配置。

## 配置说明

| 配置项/环境变量 | 作用与当前行为 |
| --- | --- |
| `server.port` / `SERVER_PORT` | HTTP 端口；dev 为 8091 |
| `spring.datasource.*` / `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` | MySQL 连接；凭据外部注入 |
| `redis.sdk.config.host/port` / `REDIS_SDK_CONFIG_HOST`、`REDIS_SDK_CONFIG_PORT` | 自定义 Redisson 连接；不是 `spring.redis.*` |
| `thread.pool.executor.config.*` | 业务线程池配置 |
| `ab.v2.enabled` / `AB_V2_ENABLED` | dev 默认 false；开启 v2 Provider/MQ 与相关调度 |
| `ab.v2.env`、`ab.v2.dubbo-group` | Topic 环境后缀与 RPC group；默认 dev / gbm-dev |
| `ab.v2.rpc.client-id/source/channel` | 可信调用方及允许的渠道范围 |
| `ab.v2.rpc.key-id/secret/verification-keys` | RPC 当前/保留验签密钥；外部配置 |
| `ab.v2.rpc.require-tls` | 默认 true；生成的开发环境文件显式允许本地例外 |
| `ab.v2.rocketmq.nameserver/access-key/secret-key` | MQ 地址和 ACL 凭据；地址默认 `127.0.0.1:9876` |
| `ab.v2.rocketmq.use-tls/require-tls` | 是否启用/要求 MQ TLS；要求 TLS 但未启用时传输不启动，Outbox 保留。dev 显式允许回环明文 |
| `ab.v2.mq.a-key-id/a-secret/b-key-id/b-secret/b-verification-keys` | A 发布及 B 消息验签/保留密钥 |
| `ab.v2.legacy-http-token` / `AB_V2_LEGACY_HTTP_TOKEN` | 旧入口 `X-Legacy-Token`；缺少配置或请求令牌时 403 |
| `ab.v2.legacy-new-orders-enabled` | 默认 false；v2 开启时旧新锁单返回 410，除非显式启用过渡开关 |
| `AB_V2_TLS_SERVER_CERT`、`AB_V2_TLS_SERVER_KEY`、`AB_V2_TLS_CLIENT_CA` | `v2-tls` profile 的服务端证书、私钥、客户端信任链路径 |

DCC 通过 Redis Topic 更新 `downgradeSwitch`、`cutRange`、`scBlacklist`；`cutRange` 校验范围为 0～100。证书/密钥应外部配置，不打印或提交。具体轮换与传输细节见[本地联调说明](docs/dev-ops/ab-v2/README.md)。

## 接口文档

### v1 HTTP 兼容入口

默认前缀 `/api/v1/gbm/` 均要求 `X-Legacy-Token`；业务响应为 `Response<T>`，业务码不能只靠 HTTP 状态判断。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| POST | `/api/v1/gbm/index/query_group_buy_market_config` | 优惠试算、团队推荐/本人摘要、团队统计 |
| POST | `/api/v1/gbm/trade/lock-market-pay-order` | 锁单；v2 启用时默认禁用新锁单 |
| POST | `/api/v1/gbm/trade/settlement_market_pay_order` | 旧支付结算 |
| GET | `/api/v1/gbm/dcc/update_config` | `key/value` 动态配置更新 |
| POST | `/api/v1/test/group_buy_notify` | 模拟回调，返回 success；不属于上述令牌保护路径 |

请求/响应定义见[旧 API 模块](group-buy-market-api/src/main/java/cn/bugstack/api)。仓库未配置 Swagger/OpenAPI UI，不提供不存在的文档访问地址。

### v2 Triple 与消息

Provider 接口包为 `cn.bugstack.api.v2`，version=`2.0.0`，默认 group=`gbm-dev`，dev Triple 端口 `50051`。客户端使用 `tri://<A地址>:50051`，并配置 TLS 与签名附件；不是 HTTP `/api/v2/gbm` 路由。

| 接口 | 方法 |
| --- | --- |
| MarketingService | `trial` |
| TradeService | `reserve`、`query`、`close` |
| TeamService | `query`、`queryReconciliation`、`completeReconciliation` |
| IntegrationService | `queryEventResult` |

RPC 返回 `RpcResult<T>`；成功码 0000。消息通过以下 Topic 交换，实际名称追加环境后缀，如 `-dev`：

| Topic 基名 | 方向/事件 |
| --- | --- |
| B_PRODUCT_EVENTS | B→A：PRODUCT_SNAPSHOT_CHANGED |
| B_TRADE_EVENTS | B→A：PAYMENT_SUCCEEDED、REFUND_STATUS_CHANGED |
| A_TEAM_EVENTS | A→B：TEAM_COMPLETED、TEAM_RECONCILIATION_REQUIRED、TEAM_FAILED |
| A_INTEGRATION_RESULTS | A→B：EVENT_PROCESSING_RESULT |

完整字段、状态和签名见[契约](docs/ab-api-contract.md)、[签名向量](docs/ab-v2-signing-vector.md)、[Java 契约包](group-buy-market-contract/src/main/java/cn/bugstack/api/v2)、[消息 Schema](docs/schemas/ab-v2-event-v1.schema.json)、[合法结果样例](docs/schemas/ab-v2-processing-result.examples.json)与[非法结果样例](docs/schemas/ab-v2-processing-result.invalid-examples.json)。处理结果按原事件类型和 outcome 校验结构，拒绝未知/缺失字段；支付嵌套事实也受约束。

## 启用 v2 本地联调

准备完成迁移的数据库、Redis 及 Docker Desktop 后，在 PowerShell 中执行现有脚本：

```powershell
./docs/dev-ops/ab-v2/start-dev.ps1
```

脚本生成/复用忽略目录 `.local` 的凭据、ACL 与环境文件，启动 RocketMQ 并创建四个开发 Topic；**不初始化 MySQL，也不启动 A**。Dubbo Provider 随 A 运行，没有独立 Dubbo Broker 容器。应用不会自动加载 `.env`，在启动 A 的同一 PowerShell 会话导入 A 文件：

```powershell
Get-Content -LiteralPath docs/dev-ops/ab-v2/.local/app.env | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
java -jar group-buy-market-app/target/group-buy-market-app.jar --spring.profiles.active=dev
```

导入前仍需配置 MySQL/Redis。`.local/app.env` 是 A 环境，`.local/b.env` 是客户端环境，不能混用 MQ 角色凭据。开发 Broker 广播 `127.0.0.1`，端口 9876/10909/10911 只绑定回环，适用于宿主机 Java 进程；容器化 A/B 必须重新规划 Broker 地址及连通性。

Triple TLS 部署使用 `--spring.profiles.active=dev,v2-tls` 并注入证书路径和 RPC 配置；MQ Broker TLS、正式证书及跨主机规则尚未完成生产验收，具体配置与验证见[联调说明](docs/dev-ops/ab-v2/README.md)。

## Docker 部署

先构建最新 JAR，再从根目录构建镜像：

```powershell
docker build -t group-buy-market:local group-buy-market-app
```

Dockerfile 复制固定 JAR 到 `/app`，提供 `JAVA_OPTS` 和 `PARAMS`，最终执行 Java 8 应用。镜像本身不包含数据库、Redis 或初始化步骤。

[应用 Compose](docs/dev-ops/docker-compose-app.yml)目前引用 `system/group-buy-market:1.0-SNAPSHOT`，只映射 HTTP 8091，并未配齐数据库、Redis、v2 凭据、Triple 50051 或证书。它与上述本地镜像标签也不同，不能宣称构建后即可直接启动。部署前须在外部部署配置中明确镜像、服务网络、环境和卷；容器中的 `127.0.0.1` 指向容器自身。生产数据持久化、可达的 MQ 广播地址及 TLS 配置**待确认**。

[阿里云镜像版环境 Compose](docs/dev-ops/docker-compose-environment-aliyun.yml)提供另一组镜像来源，仍需核对 Redis 挂载、凭据与本地服务冲突，不代表已完成生产方案。

## 验证与已知边界

[阶段测试脚本](docs/tests/run-phase4.ps1)通过前置脚本准备并写入 `group_buy_market_phase1_test`；运行前需配置 `PHASE1_DB_PASSWORD`、MySQL 客户端、Maven 和匹配结构。测试库不是稳定演示库，不要指向生产。

v2 的[真实联调脚本](docs/tests/run-ab-v2-smoke.ps1)会使用隔离库启动临时 Java 8 Provider，测试完停止自己创建的进程；TLS/独立客户端验证需额外提供现有 JDK 17+ 的 `ClientJavaHome`。Schema 验证使用[生成器](docs/dev-ops/ab-v2/generate-event-schema.py)和[校验脚本](docs/tests/validate-ab-v2-schema.py)，其 Python 验证依赖与运行命令见[联调文档](docs/dev-ops/ab-v2/README.md)。不要把默认跳过测试的 package 当作这些验证已运行。

实际 B 库存/资金/履约、精确 JDK 17、跨主机、生产 Broker TLS、长时故障及受控业务死信回放仍待确认/验收。详细证据与恢复入口：[TASK_PROGRESS.md](TASK_PROGRESS.md)、[维护清单](A-TODU.md)、[阶段交付报告](docs/phase5-ab-v2.md)。
