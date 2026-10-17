# ST-05：A 服务交付身份

2026-10-17 本机 ST-05 已验收。A 稳定源码仍为 `8040350b7e819fac813d343aab9e9ad220f4c06d`，固定镜像 ID 为 `sha256:48c7af4be50e59133cf76aac69bac7142fb0ab19c27c4cf21ae352550dc57ddc`。稳定站与 UX 站分别运行自己的 A 容器；UX 从空库初始化，未复用主站数据卷、网络或消息待办。本阶段不修改 A 业务代码、数据库契约或固定镜像。

完整的公开交付清单、逐项验收和启动／恢复说明保存在 B 项目 `D:/Java/zj-market/docs/st05-delivery-manifest.json`、`D:/Java/zj-market/docs/tests/st05-acceptance.json`、`D:/Java/zj-market/deploy/st05/README.md`。本机私有快照位于 B 的 Git 忽略目录 `target/stable-isolation/st05_20261017_02/private`，含 A 固定镜像归档；它不含在线数据卷。ST-03 历史冷备不是当前交易后的实时恢复点。异机恢复和远端发布不属于本次验收。
