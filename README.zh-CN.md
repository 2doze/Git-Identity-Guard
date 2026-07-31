# Git Identity Guard

[English](README.md) | 简体中文

Git Identity Guard 是一款 IntelliJ IDEA 插件，用于防止错误的 Git 身份进入 GitHub 提交。它会联动 IDE 中已有的 GitHub 账号，识别这些账号名下的仓库，并在提交前检查最终生效的 `user.name` 和 `user.email`。

插件绝不会静默修改 Git 配置。应用建议身份始终需要用户明确操作，而且修改范围仅限当前仓库。

## 功能特性

- 直接读取 IntelliJ IDEA 中的 GitHub.com 账号，无需重复配置访问令牌。
- 识别常见的 HTTPS、SCP 风格 SSH 和 `ssh://` GitHub Remote 地址。
- 在项目打开、仓库 Remote 变化时检查项目中的每个 Git 根目录。
- 获取并缓存每个账号的 GitHub no-reply 隐私邮箱；邮箱接口不可用时，可通过账号数字 ID 回退生成。
- 同时校验最终生效的 Git 邮箱与用户名是否匹配目标 GitHub 账号。
- 身份不匹配时显示警告，可选择应用隐私身份或忽略该仓库。
- 默认启用 Commit 前校验，可切换身份后继续、仅本次使用当前身份，或取消提交。
- 支持多 GitHub 账号、多 Remote、已验证邮箱宽松放行、自定义邮箱白名单和按仓库忽略。
- 根据 IDE 语言自动显示英文或简体中文界面。
- 身份比对全部在本地完成，无遥测，仅写入仓库级 Git 配置。

## 工作原理

1. 插件读取 IntelliJ IDEA 中已配置的 GitHub.com 账号。
2. 插件检查所有 Remote 的 fetch 和 push URL。当 Remote owner 与某个已登录 GitHub 用户名大小写不敏感地匹配时，该仓库进入保护范围；存在多个匹配 Remote 时优先采用 `origin`。
3. 插件从 GitHub 获取账号的 no-reply 邮箱。如果 `/user/emails` 未返回隐私邮箱，或当前令牌无法访问该接口，则通过 `/user` 获取账号信息并生成现代格式的 `<id>+<login>@users.noreply.github.com` 地址。
4. 插件读取该 Git 根目录中最终生效的 `user.name` 和 `user.email`，遵循 Git 的正常配置优先级。
5. 任一字段不匹配时，插件发出警告。选择**应用隐私身份**后，只会通过 `git config --local` 写入确实需要修正的字段。

扫描仓库本身不会产生任何配置修改。

## 环境要求

- IntelliJ IDEA 2024.2 或更高版本（build 242+）。
- 必须启用 IDE 内置的 Git 和 GitHub 插件。
- 至少在 **设置/偏好设置 > 版本控制 > GitHub** 中添加一个 GitHub.com 账号。
- 只有从源码构建插件时才需要 JDK 21。

## 安装

使用插件发行 ZIP 安装，无需解压：

1. 从项目 Release 获取 ZIP，或使用 `./gradlew buildPlugin` 从源码构建（Windows 使用 `.\gradlew.bat buildPlugin`）。
2. 在 IntelliJ IDEA 中打开**设置/偏好设置 > 插件**。
3. 打开齿轮菜单，选择**从磁盘安装插件...**。
4. 选择 `build/distributions/` 下的 ZIP，并按提示重启 IDE。

## 快速开始

1. 如果尚未配置账号，请在**设置/偏好设置 > 版本控制 > GitHub** 中添加 GitHub.com 账号。
2. 打开一个 Git 项目，其 GitHub Remote owner 应为该账号。
3. 检测到身份不匹配时，通过通知应用建议的隐私身份，或忽略该仓库。
4. 在**设置/偏好设置 > 工具 > Git Identity Guard** 中查看和调整插件选项。

Commit 前校验默认开启。发现身份不匹配时，可以切换身份并继续、仅本次沿用当前身份，或取消提交。

## 配置说明

| 设置项 | 默认值 | 行为 |
| --- | --- | --- |
| 启用 Commit 前校验 | 开启 | Commit 前检查受保护的 Git 根目录，身份不正确时询问处理方式。 |
| 在通知中展示当前邮箱 | 开启 | 在身份不匹配通知中显示最终生效的邮箱与用户名。 |
| 不匹配时自动开启 Commit 前校验 | 关闭 | 检测到身份不匹配后，自动重新启用 Commit 前校验。 |
| 宽松模式 | 关闭 | 放行目标 GitHub 账号的已验证邮箱或白名单邮箱；Git 用户名仍必须匹配。 |
| 邮箱白名单 | 空 | 宽松模式启用时，每行添加一个允许使用的邮箱。 |
| GitHub 账号缓存 | 自动 | 展示每个账号缓存的隐私邮箱及获取状态；**手动刷新**会重试获取并重新检查仓库。 |
| 已忽略的仓库 | 空 | 展示已忽略的仓库/账号组合，并允许取消忽略。 |

常规设置为应用级，已忽略仓库则按项目存储。

## 隐私与安全

- GitHub API 请求使用 IntelliJ IDEA 管理的凭据，插件不会保存访问令牌。
- 插件只通过 GitHub 的 `/user/emails` 和 `/user` 接口解析隐私邮箱与已验证邮箱信息。
- 解析得到的邮箱信息会缓存在 IDE 中，以避免不必要的请求；所有身份比对均在本地完成。
- 插件没有遥测，也不会访问第三方服务。
- 应用修复时，只会修改目标仓库本地配置中的 `user.name` 和/或 `user.email`，绝不会修改全局 Git 配置。

## 支持范围与限制

支持的 GitHub.com Remote 形式包括：

```text
https://github.com/owner/repository.git
git@github.com:owner/repository.git
ssh://git@github.com/owner/repository.git
```

当前版本有意不保护以下场景：

- GitHub Enterprise Server 域名。
- `git@gh:owner/repository.git` 这类 SSH 主机别名，因为仅凭 Remote 字符串无法推断别名指向的主机。
- `git://` Remote。
- Remote owner 与任何已登录账号名都不匹配的仓库，包括组织名下的仓库。

## 常见问题

### 没有出现身份警告

- 确认 Remote 指向 `github.com`，且 owner 与 IDE 中配置的 GitHub 账号匹配。
- 检查**设置/偏好设置 > 外观与行为 > 通知 > Git Identity Guard**，确认通知未被禁用。
- 打开**工具 > Git Identity Guard**，手动刷新 GitHub 账号缓存。
- 检查仓库是否位于“已忽略的仓库”列表中。

### 邮箱正确，但插件仍然提示不匹配

受保护身份由 no-reply 邮箱和作为 `user.name` 的 GitHub 登录名共同组成，任一字段不匹配都会触发保护。宽松模式只会放宽邮箱校验。

### 隐私邮箱获取失败

打开插件设置并查看账号缓存状态。如果令牌已过期，请重新登录 GitHub 账号后点击**手动刷新**。当令牌无法访问 `/user/emails` 时，插件通常会自动回退到基于账号 ID 的 no-reply 地址。

## 从源码构建

项目使用 Kotlin、Gradle 和 IntelliJ Platform Gradle Plugin。请使用 JDK 21 和仓库内置的 Gradle Wrapper。

Windows PowerShell：

```powershell
.\gradlew.bat test
.\gradlew.bat buildPlugin
```

macOS 或 Linux：

```bash
./gradlew test
./gradlew buildPlugin
```

常用开发任务：

```text
runIde        启动已安装本插件的沙箱 IDE
verifyPlugin  针对已配置的 IDE 版本执行兼容性校验
```

可安装 ZIP 会生成在 `build/distributions/` 目录中。

## 参与贡献

欢迎提交问题报告和范围明确的 Pull Request。行为变更应为相关 Remote 匹配、身份判断或状态管理逻辑补充测试，并在提交前运行测试套件。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 许可发布。
