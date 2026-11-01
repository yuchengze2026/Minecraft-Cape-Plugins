# CapeBridge v1.0

无需客户端 MOD 的 Minecraft 披风系统，包含 BC 代理、通用子服插件和网页管理端。

**本项目采用自定义非商业源码许可，商业使用须取得项目权利人的另行授权。它不是 GPL、MIT 或符合 OSI 定义的开源许可证。** 条款见 [LICENSE](LICENSE)，第三方资源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。商用授权请通过[本项目发布仓库](https://github.com/yuchengze2026/Minecraft-Cape-Plugins)联系发布者。

[下载 v1.0 正式版](https://github.com/yuchengze2026/Minecraft-Cape-Plugins/releases/tag/v1.0) · [离线 Wiki](wiki.html) · [许可协议](LICENSE)

请下载 Release 中的完整压缩包后打开 wiki.html；GitHub 文件页面展示的是 HTML 源码。

## 正式版 v1.0 组件

| 组件 | 版本 | 安装位置 |
| --- | --- | --- |
| CapeBridgeProxy | 1.0 | BC 的 `plugins/` |
| CapeBridgeBackend | 1.0 | 每个子服的 `plugins/` |
| CapeBridgeWebAdmin | 1.0 | BC 的 `plugins/` |
| 同步协议 | 7 | 代理与子服须匹配 |

主要验证客户端为 Minecraft Java 1.20.1 和 1.21.11。其他资源包版本层不代表所有版本已完整验收。代理按 BungeeCord API 编译；XCord 等分支需自行验证兼容性。

## 功能

- 静态/动态披风、独立动态橱柜、限时试穿。
- PNG 原版展开图、5:8 单体图，以及 GIF 转原生资源包动画。
- BC 统一可选资源包会话，切子服沿用会话；拒绝或加载失败不踢出玩家。
- 未装备插件披风时显示原版/SR 披风；装备后按观看者隐藏原版披风，卸下恢复。
- 贴合与动作跟随、高版本 scale 缩放。高速旋转等表现仍需实机验收，不承诺所有环境零卡顿。
- 网页上传、替换、删除、橱柜可见性设置；菜单与提示文本可配置。

## 安装与初次配置

1. 正常停服，备份现有配置、贴图和玩家选择。每个组件仅保留一个 JAR，不使用热加载器更新。
2. Proxy 和 WebAdmin 放入 BC；Backend 放入每个子服。子服另装 **PacketEvents 2.14.0**，按核心兼容性选型。
3. 启动 BC，将自动生成的 `plugins/CapeBridgeProxy/config.yml` 中 `sync-secret` 复制到所有子服的 `plugins/CapeBridgeBackend/config.yml`。不要公开此密钥。
4. 配置 BC 的资源包下载地址，使玩家能访问。默认仅监听 `127.0.0.1:25680`；其他电脑不能通过自己的回环地址下载远程服务器资源。
5. Web 默认 `http://127.0.0.1:27780/`，仅服务器本机可访问。远程管理使用隧道或 HTTPS 反向代理，并把 WebAdmin 的 `public-base-url` 设为浏览器实际访问的完整站点地址。
6. 正常重启相关实例，进入子服执行 `/cape`。资源包始终可选。

插件本身不需要数据库；其他插件是否需要数据库由其自身决定。公开包仅含默认配置，不含你的运行目录、私人披风或玩家记录。原版/SR 披风须由账号或 SR 提供，并尊重客户端披风显示选项；本插件不会为账号生成正版披风权益。

## 权限

代理权限须在 **BC 侧**配置；子服 OP 不自动获得 BC 权限。以下 `lpb` 示例要求 BC 已安装 LuckPerms。

| 权限节点 | 用途 |
| --- | --- |
| `capebridge.use` | 子服菜单，默认允许 |
| `capebridge.cape.<id>` | 代理上的单件披风佩戴权限 |
| `capebridge.cape.all` | 所有披风佩戴权限，并在橱柜看到隐藏款 |
| `capebridge.admin` | BC 管理命令及 Web 登录码；子服同名节点另控制本地诊断 |

`cape.all` 不是实际读取的节点，正确节点是 `capebridge.cape.all`。

## 查询 ID 与发放隐藏披风

在 BC 控制台执行（游戏中执行时加 `/`）：

```text
bcape list
lpb user Steve permission set capebridge.cape.example true
bcape set Steve example
bcape clear Steve
```

`example` 是示例，必须换成 `bcape list` 返回的真实 ID。`capes.yml` 下 `capes:` 的一级键也是 ID，显示名称和模型编号不是 ID。按玩家名设置时须在线；离线玩家使用 UUID。

隐藏款设置 `wardrobe-visible: false`，仅控制橱柜展示。管理员可先授予单款权限再用 `bcape set` 装备，无需给予全部披风权限。单款授权不使隐藏款在橱柜出现，卸下后需管理员重新装备，或修改可见性。

## 常用命令

| 命令 | 用途 |
| --- | --- |
| `/cape` | 披风橱柜 |
| `/dynamiccape` 或 `/dcape` | 动态披风橱柜 |
| `/cape pack` | 请求重新尝试加载资源包 |
| `/cape ver` | 显示子服与代理版本 |
| `bcape list` | BC 查询已加载披风 ID |
| `bcape reload` | 重读配置/资源并构建资源包 |
| `bcape texts` | 重载 BC 文本 |
| `capeweb` | BC 获取可点击网址与可复制的一次性登录码 |
| `/capebridge inspect <玩家>` | 子服诊断，需子服管理权限 |

## 网页登录排查

- **`/capeweb` 没权限**：在 BC 授予 `capebridge.admin`；全部披风权限和子服 OP 不能代替它。
- **网页打不开**：检查 WebAdmin 已启用。默认仅绑定服务器本机；自己电脑的 `127.0.0.1` 不是远程服务器。
- **能打开但登录失败**：重新执行 `capeweb`。登录码一次性、有时限，BC 重启后原码失效。实际地址需与 `public-base-url` 完全一致，包括协议、主机与端口；不要混用 `localhost` 和 `127.0.0.1`。
- **反向代理**：使用独立域名根路径，不支持子路径前缀；保留浏览器的 Origin，按实际 HTTPS 地址配置 `public-base-url`。

## 贴图、动画与文本

PNG 支持原版 2:1 展开图（64–512 像素宽）及 5:8 单体图。GIF 输入上限 10 MiB、512 个源帧，输出最多 64 帧，以 Minecraft 50ms tick 换算时长。最终使用 PNG 纵向帧序列与 `.mcmeta`，不保证保留 GIF 全部原始帧率。

PNG 放入 BC 的 `plugins/CapeBridgeProxy/capes/` 后执行 `bcape reload`，或通过网页上传。ID 使用小写字母、数字、下划线、短横线，长度 1–32。网页可替换、删除及切换橱柜展示；删除前自行备份。

文本位置：Backend 的 `gui.yml` / `messages.yml`，Proxy 的 `messages.yml`，WebAdmin 的 `messages.yml`。旧键 `hide-native-cape-when-loaded` 不再导致“加载即隐藏原版披风”。

## 从源码构建

安装 **JDK 21、Maven 3.9+**，在本目录执行：

```text
mvn clean verify
```

Windows：`./build.ps1 -Clean`；Linux/macOS：`sh build.sh`。依赖已缓存后才可离线构建。Windows 文件链接测试需要相应权限或开发者模式；本机 HTTP 测试需要允许回环连接。这是测试环境要求，不是插件运行必须提权。

```text
proxy/target/capebridge-proxy-1.0.jar
backend/target/capebridge-backend-1.0.jar
web-admin/target/capebridge-web-admin-1.0.jar
```

四个正式模块为 common、proxy、backend、web-admin。构建依赖由 Maven 获取，包内不附带 Minecraft 核心或第三方插件 JAR。

原版参考回归测试所需的 23 版本、18,308 组参数化姿态用例，已压缩包含在 `backend/src/test/resources/native-reference-corpus.zip`；不需要另行获取 collector 或私人研究目录。测试数据仅含合成模型姿态，不含玩家身份或世界轨迹。



特别鸣谢：GordonHim                                                                                       服务器支持：GordonHim.com

