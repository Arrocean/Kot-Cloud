# kot-cloud

`kot-cloud` 是一个基于 Kotlin 和 Micronaut 的后端脚手架，目前以模块化单体方式运行。已实现的系统模块包括管理员认证、PostgreSQL 持久化、Redis 会话、RBAC 权限控制、动态菜单数据，以及供 Apifox 导入的 OpenAPI 文档。

主启动模块为 `kot-server`。业务模块内部遵循 `adapter -> application -> domain <- infrastructure` 的 DDD 依赖方向。

## 当前能力

- Kotlin 2.4.20、Gradle 9.7.1 与 Micronaut 5.1.5（Platform BOM）。
- 基于 Micronaut Data JDBC、HikariCP 和 PostgreSQL 的数据访问。
- JWT access token、Redis 会话及可配置密码编码器。
- 管理员注册、登录、登出和当前用户信息接口。
- 角色、权限、菜单、用户角色分配，以及 `@RequirePermission` 权限校验。
- 统一 `CommonResult<T>` 响应包装和全局异常处理。
- OpenAPI 3.0.1 生成，并提供 Apifox 专用导入文档。
- Docker 与 Kubernetes 部署配置骨架。

## 模块说明

| 模块                         | 职责                                                    |
|----------------------------|-------------------------------------------------------|
| `kot-framework`            | 公共模型，以及 Data、PostgreSQL、安全和 Web 相关 Micronaut starter。 |
| `kot-module-system-api`    | 系统模块公开契约、常量和枚举。                                       |
| `kot-module-system-server` | 系统模块 DDD 实现，包含认证、用户、登录日志和 RBAC。                       |
| `kot-server`               | 单体 Micronaut 聚合启动模块。                                  |
| `kot-gateway`              | 网关模块预留。                                               |
| `kot-module-member`        | 会员模块预留。                                               |

完整目录结构见 [FILEPATH.md](FILEPATH.md)，RBAC 架构决策见 [docs/adr/0001-rbac-authorization.md](docs/adr/0001-rbac-authorization.md)。

## 环境要求

- JDK 25，与当前 Docker 运行时镜像保持一致。
- PostgreSQL 18，或兼容版本。
- Redis 7，或兼容服务端。
- Git。项目已包含 Gradle Wrapper，无需单独安装 Gradle。

当前默认运行依赖 PostgreSQL 和 Redis。MariaDB、网关和会员模块仍为预留模块，不是已完成的运行时选项。

## 本地安装

1. 克隆仓库并进入项目根目录。

   ```powershell
   git clone <repository-url> kot-cloud
   Set-Location kot-cloud
   ```

2. 创建 PostgreSQL 数据库。

   ```sql
   CREATE DATABASE kot_cloud;
   ```

3. 导入系统表结构和 RBAC 初始化数据。该脚本会删除并重建系统表，请勿在需要保留数据的数据库上直接执行。

   ```powershell
   psql -h 127.0.0.1 -p 5432 -U postgres -d kot_cloud -f .\sql\kot_cloud_system.sql
   ```

4. 启动 Redis，并确保其可通过配置的 `REDIS_URI` 访问。

5. 配置连接信息与运行密钥。以下 PowerShell 示例使用默认本地端口。

   ```powershell
   $env:JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/kot_cloud"
   $env:JDBC_USER = "postgres"
   $env:JDBC_PASSWORD = "replace-with-a-database-password"
   $env:REDIS_URI = "redis://127.0.0.1:6379/0"
   $env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"
   ```

`kot-server/src/main/resources/application-local.properties` 包含机器相关的连接信息，因此不作为安装流程的一部分。本地、CI 和生产环境均建议优先使用环境变量。

## 启动项目

在项目根目录使用 Gradle Wrapper。当前 Windows 工作区约定将 Gradle 缓存放在系统盘以外：

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-server:run
```

默认端口为 `1164`，服务地址为 `http://localhost:1164`。

如需打包后运行：

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-server:installDist
.\kot-server\build\install\kot-server\bin\kot-server.bat
```

## 构建与验证

编译系统模块：

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-module-system:kot-module-system-server:compileKotlin
```

构建单体应用：

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-server:build
```

`gradle.properties` 中的 `ksp.incremental=false` 为有意设置，用于规避当前 Micronaut OpenAPI KSP 聚合输出在增量 PSI 状态下的生成问题。

## 认证与授权

- 管理员注册和登录为匿名接口。
- 注册成功后会自动绑定可编辑的 `system_admin` 角色。
- 受保护接口使用 `Authorization: Bearer <accessToken>`。
- `super_admin` 仅由数据库运维人员初始化和维护，业务 API 不会创建、修改、分配、撤销或删除该角色。
- 数据表中保留了 `tenant_id` 作为后续准备；当前运行行为仍为单租户。

运行时路由使用版本化前缀，例如 `/v1/admin-api`。完整接口、请求参数和响应模型请以生成的 OpenAPI 文档为准。

## OpenAPI 与 Apifox

OpenAPI 文档会在资源处理阶段生成。需要单独生成 Apifox 文档时执行：

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-module-system:kot-module-system-server:generateApifoxOpenApi
```

应用启动后可通过以下地址访问：

- 完整运行时路由文档：`http://localhost:1164/swagger/kot-cloud-api-1.0.0.yml`
- Apifox 导入文档：`http://localhost:1164/swagger/kot-cloud-apifox.yml`

Apifox 文档保留服务器 URL，只在 OpenAPI 顶层 `paths` 中移除 `/v{n}/admin-api` 与 `/v{n}/app-api` 前缀。请按 OpenAPI 3.0 YAML 导入，不要转换为 OpenAPI 3.1。

## 部署说明

`docker/Dockerfile` 用于打包 `kot-server` 的发行归档，`docker/k8s` 提供 Kustomize 覆盖配置。当前 `docker-compose.yml` 仅是应用容器骨架，并未提供完整 PostgreSQL 和 Redis 开发环境；部署前需要补充数据库、Redis、JWT 与端口配置。

## 参与贡献

1. 创建功能分支。
2. 保持 DDD 依赖方向不被破坏。
3. 对修改模块执行对应的编译或构建验证。
4. 提交 Pull Request，并说明已执行的验证命令。
