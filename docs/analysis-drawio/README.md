# group-buy-market 难点/亮点 .drawio 图解

> 打开方式：用浏览器访问 https://app.diagrams.net → 「File → Open from → Device…」，
> 或安装 draw.io 桌面版直接双击本目录下的 `.drawio` 文件。
> 所有图片均为可编辑 XML，可用 draw.io 继续改布局、加注释。

## 图例（配色约定）
| 形状/颜色 | 含义 |
|---|---|
| 🟢 绿色圆角 | 开始 / 正常结束 |
| 🔵 蓝色圆角 | 处理步骤 / 领域动作 |
| 🔶 黄色菱形 | 判断分支（是 / 否） |
| 🟣 紫色圆柱 | MySQL 表/记录操作（含关键 SQL） |
| 🟠 橙色圆柱 | Redis 操作（锁 / 位图 / Pub/Sub / bucket） |
| 🟠 橙色方框 | 对外 HTTP 调用（第三方） |
| 🔴 红色方框 | 失败 / 拦截出口（含业务异常码） |
| 🔴 红色虚线框 | ⚠ 坑 / 值得商榷的点（阅读重点） |
| 🟡 黄色虚线框 | 💡 结论一句话 / 补充要点 |
| 灰色虚线框 | 分区标题（分面板） |

连线上的文字 = 分支条件 / 传递的返回值。

---

## 每张图的内容索引

### 4.1-拼团锁单并发控制.drawio
- 主干：参团请求 → 规则链三道校验 → 是否有 teamId？
- **是/参团**：`UPDATE ... SET lock_count=lock_count+1 WHERE ... AND lock_count<target_count`（条件更新原子防超员）→ 影响行数==1?
- **否/开新团**：INSERT 团记录（lock_count=1，右外列单独一条分支）
- 两条路合并 → 插用户明细（biz_id 唯一索引幂等）→ 撞唯一键？→ 成功 / INDEX_EXCEPTION
- 🔴 坑1：`updateSubtractionLockCount` 无人调用（占位不回收）；坑2：唯一索引兜住"统计次数"的非原子窗口。

### 4.2-成团判定与可靠结算.drawio
- 主干：支付回调 → 结算规则链(读团快照) → 同一事务里 ①明细支付 ②团人数+1(带守卫)
- 🔶 `targetCount-1 == completeCount(快照)` 是不是"最后一单"？
  - 否 → 团未满，return false
  - 是 → ③团置完成 ④收集已支付单号 ⑤同事务写 notify_task → return true → 立即触发回调
- 🔴 大坑：快照判定在"两人同时付最后一笔"时会永不完成团（含修复思路）；明细更新缺 status=0 过滤/支付幂等键。

### 4.3-无MQ的可靠通知.drawio
- 主流程：两个入口(成团即发 + 15s 定时扫描) → 取待投递任务 → RLock 抢锁 → 抢不到=空执行
- 抢到 → OkHttp 回调 → 按 success / error / null 三分支更新状态机(0待处理/1成功/2重试/3失败)
- 🔴 状态机方向疑似写反：<5 次置 3(反而不再被扫)、≥5 次置 2(反而无限重试)。

### 4.4-DCC动态配置中心.drawio
- 上半：启动装配（BeanPostProcessor 扫 @DCCValue → 读写 Redis bucket → 反射注入 → 注册频道监听）
- 下半：运行期热更新（curl → publish → 监听写 bucket → 反射热更字段）
- 底部：三个消费点（SwitchNode 降级/切量、SCRuleFilter 黑名单、TradeRepository 委托）。

### 4.5-营销试算策略树与折扣策略.drawio
- 树：请求 → RootNode 校验 → SwitchNode 开关/切量 → MarketNode(并行查活动+SKU, 5s超时)
- → 按 marketPlan 从 Map 取 ZJ/ZK/MJ/N 折扣策略算价 → TagNode 人群 → EndNode 返回
- 黄色要点：模板方法 + 策略 Map 注册表，新增玩法只加一个 @Service("XX") 类。

### 4.6-责任链短路与人群位图与六边形架构.drawio
- 三个面板：
  A. 责任链 `BusinessLinkedList` 遍历+首个非 null 短路
  B. Redis RBitSet：userId→MD5取模→bit 下标，写入/三处读取，⚠碰撞误判
  C. 六边形：Controller → domain(仅依赖 ITradeRepository/ITradePort) → infrastructure 两实现 → 第三方

---

> 由脚本自动生成（坐标近似，个别长连线可能穿绕）；打开后可微调位置。
> 若某张图想要"某一支再展开一层 / 换成左右布局 / 拆成小图"，把图名告诉我即可重新生成。
