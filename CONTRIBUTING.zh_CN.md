# 参与 kot-cloud 贡献

感谢你有兴趣为 `kot-cloud` 做出贡献！本文档说明贡献者许可协议、贡献流程、Pull Request 要求，以及如何搭建开发环境。

## 1. 贡献者许可协议（CLA）

向你提交 Pull Request、补丁、Issue 评论或任何其他贡献时，即表示你同意以下条款：

1. 你授予项目所有者（Arrocean）及本软件的所有接收方一项永久性、全球范围、非独占、免费、免版税且不可撤销的著作权与专利许可，允许其将你的贡献作为 `kot-cloud` 的一部分，按现有的 [MIT 许可证](LICENSE)（或项目所有者未来采用的其他许可证）进行使用、复制、修改、公开展示、再许可和分发。
2. 你确认你的贡献是你本人的原创成果（或你有权提交该成果），并且你在法律上有权授予上述许可。
3. 贡献按"原样"提供，不附带任何形式的保证或条件。

**无需签署任何文件。提交 Pull Request 即视为完全接受本协议。** 如不同意上述条款，请勿提交贡献。

## 2. 如何贡献

所有贡献必须走 Fork 工作流，不接受直接推送到本仓库。

1. **Fork** 本仓库到你自己的账号下。
2. **克隆你的 Fork** 到本地：

   ```bash
   git clone https://<代码托管平台>/你/kot-cloud.git
   cd kot-cloud
   ```

3. **为你的改动创建分支**：

   ```bash
   git checkout -b feat/my-feature
   ```

4. **进行修改。** 每个 Pull Request 只聚焦一处改动；不相关的改动请拆分为多个 Pull Request。
5. **在本地构建并测试**（见[开发环境配置](#4-开发环境配置)），确保 `./gradlew build` 通过。
6. **将分支推送到你的 Fork**，并向本仓库的 `master` 分支发起 Pull Request。

对于较大的改动（新模块、架构调整、协议变更），请先发起 Issue 讨论设计方案，再动手写代码。

## 3. Pull Request 规范

从简。一个 Pull Request 需要包含：

**标题格式**

```
type(scope): 简短概述
```

- `type` 取值：`feat`、`fix`、`refactor`、`docs`、`test`、`chore`、`build`。
- `scope` 可选，标明影响范围，如 `gateway`、`system`、`member`、`framework`、`server`。
- 概述为简短的祈使句，例如 `feat(gateway): add route-level identity check`。

**描述**

- 说明改动做了什么、为什么需要（小改动一两句话即可）。
- 说明如何验证（命令、接口或手动步骤）。
- 附上简短清单：

  - [ ] 本地 `./gradlew build` 通过
  - [ ] 新增行为在可行范围内有测试覆盖
  - [ ] 如改动涉及文档（`README` / `docs/`），已同步更新

合并时维护者可能会对提交做 squash 或 rebase 处理。

## 4. 开发环境配置

### 前置条件

| 工具       | 版本                     | 说明                                       |
|------------|--------------------------|--------------------------------------------|
| JDK        | 21 或更高                | GraalVM 可选，仅 `kot-server` 原生镜像需要 |
| PostgreSQL | 任意受支持版本           | 本地实例或远程测试库                       |
| Redis      | 任意受支持版本           | 令牌会话所必需                             |
| Git        | 最新版                   |                                            |

Gradle 通过 Wrapper 调用（`./gradlew`，Windows 下为 `gradlew.bat`），无需本地安装 Gradle。项目使用 Kotlin 2.4.20、Gradle 9.7.1 与 Micronaut 5.1.5（Platform BOM）。

### 配置

各服务从自身 `src/main/resources` 下的 `application.properties` 读取配置，并可通过环境变量覆盖：

| 变量                                        | 使用方                              | 用途                                         |
|---------------------------------------------|-------------------------------------|----------------------------------------------|
| `JDBC_URL`                                  | `kot-server`、system                | PostgreSQL JDBC URL                          |
| `JDBC_USER` / `JDBC_PASSWORD`               | `kot-server`、system                | 数据库凭据                                   |
| `REDIS_URI`                                 | `kot-server`、`kot-gateway`、system | Redis 连接串，如 `redis://127.0.0.1:6379/0`  |
| `JWT_SECRET`                                | 全部服务                            | 共享 JWT 签名密钥（至少 32 字节）            |
| `PASSWORD_ENCODER`                          | `kot-server`、system                | `pbkdf2`（默认）、`bcrypt` 或 `argon2id`     |
| `GATEWAY_PORT`                              | `kot-gateway`                       | 网关监听端口（默认 `8080`）                  |
| `SYSTEM_SERVICE_URL` / `MEMBER_SERVICE_URL` | `kot-gateway`                       | 下游路由目标（默认 `http://127.0.0.1:1164`） |

`kot-server` 另附 `application-local` 环境，内置一份开箱即用的本地配置。

### 运行

```bash
# 单体（默认端口 1164）
./gradlew :kot-server:run

# 独立 system 模块（system 环境，端口 1164）
MICRONAUT_ENVIRONMENTS=system ./gradlew :kot-module-system:kot-module-system-server:run

# 网关（默认端口 8080，默认路由到 http://127.0.0.1:1164）
./gradlew :kot-gateway:installDist
./kot-gateway/build/install/kot-gateway/bin/kot-gateway
```

### 测试与构建

```bash
./gradlew build          # 编译并测试全部模块
./gradlew test           # 仅运行测试
./gradlew :kot-gateway:test
```

如果服务能启动但行为异常，请先查看启动日志；本地开发中的多数运行时故障来自 PostgreSQL 或 Redis 无法连接。

## 5. 致谢

每一份贡献都很重要，无论是一行错别字的修复，还是一个全新的模块。感谢你愿意花时间让 `kot-cloud` 变得更好——祝编码愉快！
