# ShitBotUltra MC API 计划

更新日期：2026-11-14。状态：独立仓库本次仅初始化计划、代理入口与许可证，尚未实现 Java 类、Maven 工程或发布依赖。本计划不授权立即实现、构建或测试。

## 目标与边界

给 Minecraft 端及其他 Java 扩展提供容易调用的依赖，将与 Ultra Core 对接的方法和通信实现集中在此仓库。Minecraft 平台模块只处理平台事件、数据转换、动作与线程调度，不自行实现第二套连接。

本仓库使用 Java，公共根包为 `shitbotteam.ultra.minecraft.api`，通信客户端为 `shitbotteam.ultra.minecraft.bridge`。建议拆成纯 Java API 与通信客户端两个模块；不引入 Bukkit / Paper / Folia / Velocity 等平台依赖，不连接 QQ 或 Discord，不持有业务数据库，不渲染图片。

## 计划提供的接口

| 范围 | 能力 |
| --- | --- |
| 会话与生命周期 | 建立 / 关闭连接、认证、握手、节点能力、健康状态和重连 |
| 上报 | 登录请求、聊天、玩家进退与状态 / 背包快照 |
| 业务请求 | 绑定 / 白名单查询与授权操作、统一消息和图片请求，Core 决定权限 |
| 游戏动作 | 注册能力与处理器，接收 Core 请求并返回动作结果 |
| 数据提供器 | 向 Core 提供状态、批量占位符等结构化数据 |
| 异步与错误 | 请求关联、deadline、取消、离线、超时、不支持与结果未知 |
| 事件与诊断 | 订阅事件、取消订阅、状态通知与脱敏日志 |

远程调用使用 CompletionStage / CompletableFuture 等异步结果，回调线程不保证游戏线程。平台调用者负责 Bukkit / Folia 等调度；同步 PAPI 只读取有期限的缓存，不同步等待网络。

同一个 Minecraft 实例只维护一个受管理的 Core 客户端，第三方插件获取其公开服务后复用。公开 API 不由每个插件分别打包成冲突的类副本，客户端实现与平台服务发布方式在实施时说明。

## 依赖、协议与发行

- 依赖方向为 Minecraft 平台模块 → MC API / 客户端 → Core Bridge；本仓库不依赖 Minecraft 平台实现。
- Core 的 api/bridge/ 是唯一协议来源，Java 仓库固定协议版本与摘要，不维护独立变种。
- 建议 Maven groupId 为 `shitbotteam.ultra`，制品为 `ultra-minecraft-api` 与 `ultra-minecraft-client`；实际发布命名空间资格、仓库地址、Java 目标和版本实施时确定，不编造已可下载坐标。
- API 保持窄接口，DTO、参数和结果不泄漏客户端实现。记录 Core / Bridge / API / Minecraft 的兼容范围和废弃版本。
- API 作为开发依赖由开发者更新版本，不给依赖 JAR 设计运行中自更新或热卸载。

## 后续任务顺序

1. 固定 API 服务边界、错误码、异步 / 线程 / 取消语义及 Java 目标版本。
2. 根据 Core Bridge 固定契约实现公共 DTO 和客户端；集中认证、请求、重连与有界队列。
3. 为 Minecraft 首个平台提供最小调用路径，说明客户端所有权、平台调度与服务获取方式。
4. 补充绑定 / 白名单、消息 / 图片和数据提供器接口，以及 Java 开发者依赖示例与发行兼容表。

这些是后续计划，不代表已实现或已验证；测试由维护者自行完成。

## 公共文档

- [总体规划](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/docs/plan/01-总体规划.md)：五仓库、语言与进程。
- [仓库结构](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/docs/plan/02-仓库结构与开发规范.md)：Java 包、模块和依赖方向。
- [协议配置与迁移](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/docs/plan/03-协议配置与迁移.md)：Bridge、登录、超时、去重和原 Java API 迁移。
- [扩展接口与数据导入](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/docs/plan/06-扩展接口与数据导入.md)：独立 API、平台中立身份、图片 / 插件接口和导入。
- [公共代理规范](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/AGENTS.md)与[贡献指南](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/CONTRIBUTING.md)：只写计划、测试边界、分批签名提交、push 和 Verified。

本仓库提供完整 [AGPL-3.0-only LICENSE](LICENSE)，统一许可说明见 [Core 许可文档](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/docs/licensing.md)。README 与实际工程留待维护者指定任务后再写。
