# 平台接入与服务所有权

使用顺序为：配置节点 → 注册真实能力与快照提供器 → 建立一个 ManagedBridgeClient → 将 BridgeClient 发布为平台服务 → 监听平台事件 → 停服时 close。第三方插件只依赖 api 并取得已有服务，不独立连接 Core。

## 配置与依赖

Core 节点配置的 id、network_id、server_id、identity_mode 与客户端选项必须一致。节点 Token 与管理 Token 分离。权威节点提供 SnapshotProvider，并在 Core 中设置 session_authority。负责登录拦截的节点还要匹配 login_gate。

API 可运行目标为 Java 8；现代平台可以使用自己的较新 JRE。旧版本游戏平台是否真的支持目标 Java / TLS / 依赖，需维护者自行验证，不能从 source release=8 推断所有平台兼容。

平台主插件引入 client（它依赖 api），提供唯一的公共 API 类与实例。其他插件将 api 标记为 provided / compileOnly，并在平台描述中声明对主插件的依赖。不要将公共 api 包重定位为每个插件自己的副本。若主插件需要避免与服务器预装库冲突，打包时可重定位客户端使用的 Gson / OkHttp / Okio / Kotlin 等依赖，保留单份公共 API；不能只重定位其中一个库而遗忘传递依赖。

没有远程仓库地址或已发布 release 的承诺；先在维护者自己的发布流程中生成、核对并发布固定版本，再让平台模块锁定该版本。

## 当前接口映射

| Java 调用 / 注册 | Bridge type | 说明 |
| --- | --- | --- |
| authorize(player, timeout) | login.authorize | 异步登录决策，拒绝可能包含一次性验证码 |
| binding(name, timeout) | binding.get | 查询白名单 / 是否绑定，NOT_FOUND 是业务错误 |
| chat(message) | chat.message | 玩家聊天；保留非空 origin_id 防止回环 |
| joined / left | player.joined / player.left | 由会话权威节点按顺序发布 |
| players | players.snapshot | 主动一致性快照；重连的强制快照由 SnapshotProvider 管理 |
| inventory(snapshot) | inventory.snapshot | 上报已采集快照，不在网络线程读取游戏背包 |
| serverReady | server.ready | 游戏实际就绪时上报；Core 按 boot ID 去重 |
| action(CHAT_SEND, handler) | chat.send | 广播；传递 origin_id 到游戏聊天转发协作层 |
| action(PLAYER_KICK, handler) | player.kick | 在正确玩家 / 区域线程执行 |
| action(COMMAND_EXECUTE, handler) | command.execute | 游戏控制台命令，绝不能传给系统 shell |
| action(INVENTORY_READ, handler) | inventory.read | 返回同一玩家的 InventorySnapshot |
| action(TPS_READ, handler) | tps.read | 返回实际 ServerPerformance；不支持时不注册 |
| action(PERMISSION_CHECK / PLACEHOLDER_RESOLVE, handler) | 同名 | 平台自定 JSON 形状，Core 透传授权请求 |

通用 request 保留扩展通道，不会让 Core 自动拥有新能力。社交消息发送、图片渲染与绑定写操作需要将来明确扩展 Core 的游戏端协议；此版本不从游戏端转用管理员 API。

## 登录与平台线程

登录事件必须使用平台支持的异步 / 挂起机制，不能在主线程同步等待 authorize。future 出错时由平台明确决定离线策略，建议拒绝新的登录并提示 Core 不可用；不要把异常转成 allowed=true。验证码只显示给相关玩家，不写日志。

普通 Bukkit 与代理的平台调度不同，Folia 还区分实体 / 区域 / 全局调度。客户端不做这些平台判断。handler 接到请求后返回 stage，实际任务在正确线程执行，执行前检查取消令牌；此后发生异常或不确定结果应保留 RESULT_UNKNOWN。

对聊天回环，可以在平台转发层标记 Core 广播产生的事件，并回传相同 origin_id。不要仅依赖文本相同判断来源，也不要把所有玩家输入都标成 Core 来源。

## 快照与占位符

SnapshotProvider 的顺序约束见[客户端语义](client-semantics.md)。完整示例在 [PlatformBridge.java](../examples/PlatformBridge.java)，其中 Platform 是必须由真实插件实现的接入边界，不是内置 Bukkit 适配器。

同步 PAPI 只读取 ExpiringValue / 平台缓存。由独立异步流程调用 binding / 其他可用方法更新快照，TTL 到期或身份发生变化时使其失效。缓存未命中显示未知 / 空值，不在占位符回调内 get / join 网络 future。ExpiringValue 只保存你给的对象，调用者必须保证该对象不可变。

## 维护者后续验证

本次只检查源码、Java 8 语法、模块依赖方向、协议来源摘要及 patch 重放。编译、自动化测试、真实 WebSocket、Token 轮换、断线快照、各平台线程规则、关闭与类加载冲突均需维护者自行验证。没有添加自动运行的测试或 CI。
