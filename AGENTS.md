# MC API 代理工作规范

本文件是本仓库直接执行入口。公共详细规则以 [Core AGENTS.md](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/AGENTS.md) 为准；本地 agent.md 只做导航。用户当前明确要求优先。

## 当前范围

- 本仓库已进入 Java API / 客户端源码实现阶段；当前范围以 README、协议来源记录及维护者本次具体指令为准，不自动实施其他组件或未开放的 Core 协议。
- 不运行测试、构建、应用或手动触发 CI，测试由维护者自行完成；可以阅读源码、检查 Java 语法、契约、差异与 Git 状态。不得把静态检查称为编译、类型检查或功能验证。
- 先确认当前仓库、分支和未提交修改，再从 [plan.md](plan.md) 按任务阅读；保留用户改动，不自行扩展工作范围。

## 本仓库边界

本仓库负责独立 Java API 与 Core 通信客户端，公共包为 shitbotteam.ultra.minecraft.api，通信实现为 shitbotteam.ultra.minecraft.bridge。集中提供认证、连接、请求、事件、重连、超时与异步结果，不依赖 Bukkit / Folia 等平台，不连接社交平台或读写业务数据库。

协议唯一来源为 Core 的 api/bridge/，记录来源版本和摘要。Minecraft 仓库通过固定版本依赖调用本仓库，不再建立第二份 bridge-api / bridge-common。远程调用不可阻塞游戏线程，平台调度由调用端负责。

## 公共规范与交付

新增功能或 Bug 修复的 PR，先在对应组件仓库提交 Issue，让作者了解并沟通后再提交，并在 PR 正文关联该 Issue。人工与代理辅助贡献都适用；详细流程见 Core 贡献指南。

同级克隆时读取 ../ShitBotUltra/AGENTS.md；没有 Core 克隆时读取上面的 GitHub 链接，按任务进入公共计划，不假设跨仓库规则自动加载。公共架构不在本仓库复制维护。

当前任务按模块交付 patch、应用基线和建议 Conventional Commit 信息，由维护者自行应用。不要沿用历史文档任务的 commit / push 授权。不加助手署名或 AI Co-authored-by，不关闭维护者提交签名，不重写历史；未来实际推送时再核对 GitHub Verified。

提交格式见 [贡献指南](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/CONTRIBUTING.md)，许可边界见 [许可说明](https://github.com/ShitBot-Team/ShitBotUltra/blob/main/docs/licensing.md)。本仓库提供完整 [AGPL-3.0-only LICENSE](LICENSE)。最终如实说明源码与文档范围、补丁交付、未运行测试 / 构建；没有实际提交或推送时不声称 Verified 结果。
