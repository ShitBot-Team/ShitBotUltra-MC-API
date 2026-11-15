# ShitBotUltra MC API

平台中立的 Java 8 异步 API 与 Ultra Core Bridge v1 客户端。Minecraft 平台模块负责采集数据、登录拦截和游戏线程调度；本仓库集中维护连接、认证、协议编解码、请求、事件、超时、重连和动作去重。

**当前状态：源码实现与静态 review，未编译、运行测试或联调。** 本仓库不包含 Bukkit / Paper / Folia / Velocity / BungeeCord / Nukkit 平台实现。开发者接入方式见 [接入说明](docs/integration.md)，协议行为见[客户端语义](docs/client-semantics.md)。

## 模块

| 模块 | 坐标与包 | 用途 |
| --- | --- | --- |
| `api` | `shitbotteam.ultra:ultra-minecraft-api:0.1.0-SNAPSHOT`；`shitbotteam.ultra.minecraft.api` | 无外部依赖的公共 DTO、异步服务、动作、快照与有期限缓存 |
| `client` | `shitbotteam.ultra:ultra-minecraft-client:0.1.0-SNAPSHOT`；`shitbotteam.ultra.minecraft.bridge` | 客户端、配置、Payloads 辅助、Gson 严格编解码与 OkHttp 传输 |

这是本地源码工程的 Maven 坐标，**没有宣称已发布到 Maven Central 或其他公共仓库**。不添加猜测的下载地址。父工程固定 Java release 8 与依赖版本；可供维护者使用 Maven 进行后续构建，本次没有执行 Maven、javac 或测试。

客户端固定 OkHttp 4.12.0、Gson 2.13.2；前者带有 Okio / Kotlin 传递依赖。API 的公开签名只使用 JDK 类型及本项目模型，不泄漏 Gson、OkHttp 或游戏对象。许可与来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 当前能力

- `ManagedBridgeClient` 管理单个节点的连接、Bearer 认证、握手、心跳、重连、关闭与脱敏状态订阅。
- `authorize`、`binding` 与通用 `request` 关联异步请求；当前 Core 的游戏端业务请求只有 `login.authorize`、`binding.get`。
- 上报聊天、玩家加入 / 离开、在线快照、背包快照与服务器 ready；返回 Core 处理确认，不承诺外部消息送达。
- 重连时按 Core 要求取得新鲜权威快照，等待确认后恢复增量，不自动重发未确认动作或旧聊天。
- 按注册的能力接收游戏动作；有界并发、期限、取消令牌、幂等键 / payload 一致性与去重结果缓存。
- `Payloads` 对应广播、踢人、控制台命令、背包与游戏性能模型。`permission.check` / `placeholder.resolve` 由平台通过授权的自定义 JSON 实现。
- `ExpiringValue<T>` 供同步占位符读取先前异步更新的不可变快照，不在占位符回调里等待网络。

当前 Core Bridge 没有开放社交消息发送、模板渲染、绑定写入或绑定变更订阅接口。这些是后续协议扩展项，本客户端没有用管理 Token、业务数据库访问或返回假成功来替代它们。平台也不持有社交机器人凭据。

## 协议来源与兼容

协议唯一来源是 [Core api/bridge/v1](https://github.com/ShitBot-Team/ShitBotUltra/tree/main/api/bridge/v1)。本地只保存固定快照及摘要用于 review，不另行维护变体：

- [协议来源记录](protocol/bridge-v1/SOURCE.json)
- [JSON Schema](protocol/bridge-v1/envelope.schema.json)
- [原始 Go 契约](protocol/bridge-v1/types.go)

当前快照对应本次会话交付的 **Core `0001` 至 `0007` 补丁完成后的工作树**，不是已发布的 Core tag / commit。详细树摘要记录在 SOURCE.json；应用 MC API 补丁不等于已更新 Core。

Bridge major 为 1。未提供跨平台兼容性结果，也未承诺旧版 ShitBot Java API 二进制兼容。公共 API 只由平台主插件提供一份，其他插件通过平台服务注册机制取得同一个 `BridgeClient`；不要每个插件各建连接或各打包一份公共类。

## 最小接入形状

```java
ClientOptions options = ClientOptions.builder(
    URI.create("ws://127.0.0.1:17880/bridge/v1"),
    "survival", "main", "survival", IdentityMode.OFFLINE)
    .token(() -> cachedNodeToken)
    .metadata("0.1.0", actualPlatformName, actualMinecraftVersion)
    .snapshots(platformSnapshotProvider)
    .action(Capabilities.CHAT_SEND, platformChatHandler)
    .build();
BridgeClient client = new ManagedBridgeClient(options);
client.start();
```

示意中的变量由平台提供；[完整平台中立接入示例](examples/PlatformBridge.java)定义了这些职责。回调不保证处于游戏线程，不能在游戏线程调用 `get()` / `join()` 等待网络。真实平台插件属于 `ShitBotUltra-Minecraft` 仓库。

跨仓库规划入口为 [plan.md](plan.md)。许可证为完整的 [AGPL-3.0-only](LICENSE)，第三方依赖保留各自许可。
