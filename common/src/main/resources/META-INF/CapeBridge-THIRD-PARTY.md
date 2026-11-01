# 第三方内容与授权边界

自定义非商业许可仅覆盖作者有权许可的部分，不重新许可第三方作品。发布者须独立确认分发满足第三方条款；本文件不代表第三方再分发权利已获批准。

## Minecraft 资源

- `proxy/src/main/resources/camera/` 含由 Minecraft Java 官方客户端着色器改写的资源，原始权利属于 Mojang/Microsoft。来源见该目录 `NOTICE.txt`，版本与客户端 SHA-1 见 `proxy/src/main/resources/compat/provenance.json`。
- `proxy/src/main/resources/compat/` 含与原版资源包兼容的模型/物品模板和版本元数据。源自第三方的部分保留其权利，不受本项目许可证重新授权。
- `proxy/src/main/resources/default-elytra.png` 是历史保留的 Minecraft 1.20.1 纹理，当前 PackBuilder 不引用；并非本项目原创。
- 发布包不含用户上传披风、Minecraft 客户端 JAR 或服务端核心。

## Maven 依赖

依赖版本与范围见各模块 POM。本次构建打包本项目 common 模块；BungeeCord API、Spigot API、PacketEvents、JOML 与 Gson 按现有 POM 的 provided 范围使用，JUnit 为测试依赖。它们保留各自许可证，使用第三方服务端/插件须另行遵守其条件。

- BungeeCord：<https://github.com/SpigotMC/BungeeCord>
- Spigot API：<https://hub.spigotmc.org/stash/projects/SPIGOT/repos/bukkit/browse>
- PacketEvents：<https://github.com/retrooper/packetevents>
- JOML：<https://github.com/JOML-CI/JOML>
- Gson：<https://github.com/google/gson>
- JUnit：<https://github.com/junit-team/junit5>

本项目与 Mojang/Microsoft 无官方从属或背书关系。
