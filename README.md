# kot-cloud

`kot-cloud` is a Kotlin and Micronaut backend scaffold organized as a modular monolith. The current implementation provides the system module with administrator authentication, PostgreSQL persistence, Redis-backed token sessions, RBAC authorization, dynamic menu data, and OpenAPI documents for Apifox.

The primary application is `kot-server`. It aggregates business modules while preserving the dependency direction `adapter -> application -> domain <- infrastructure` inside each server module.

## Current Capabilities

- Kotlin 2.4.20, Gradle 9.7.1, and Micronaut 5.1.5 (Platform BOM).
- PostgreSQL persistence through Micronaut Data JDBC and HikariCP.
- JWT access tokens with Redis-backed sessions and configurable password encoders.
- Administrator registration, login, logout, and current-profile endpoints.
- RBAC roles, permissions, menus, user-role assignments, and permission enforcement through `@RequirePermission`.
- Consistent `CommonResult<T>` response envelopes and centralized exception handling.
- OpenAPI 3.0.1 generation, including an Apifox-oriented document.
- Docker and Kubernetes deployment configuration templates.

## Modules

| Module                     | Responsibility                                                                               |
|----------------------------|----------------------------------------------------------------------------------------------|
| `kot-framework`            | Common models and Micronaut starter modules for Data, PostgreSQL, Security, and Web support. |
| `kot-module-system-api`    | System-module public contracts, constants, and enums.                                        |
| `kot-module-system-server` | System-module DDD implementation: authentication, users, login logs, and RBAC.               |
| `kot-server`               | Monolithic Micronaut bootstrap application.                                                  |
| `kot-gateway`              | Reserved gateway module.                                                                     |
| `kot-module-member`        | Reserved member module.                                                                      |

See [FILEPATH.md](FILEPATH.md) for the maintained repository tree and [docs/adr/0001-rbac-authorization.md](docs/adr/0001-rbac-authorization.md) for the RBAC decision record.

## Prerequisites

- JDK 25, matching the current Docker runtime image.
- PostgreSQL 18 or a compatible PostgreSQL version.
- Redis 7 or a compatible Redis server.
- Git. Gradle is provided through the included wrapper.

The project currently uses PostgreSQL and Redis by default. MariaDB, gateway, and member modules are placeholders rather than completed runtime options.

## Local Setup

1. Clone the repository and enter its root directory.

   ```powershell
   git clone <repository-url> kot-cloud
   Set-Location kot-cloud
   ```

2. Create the PostgreSQL database.

   ```sql
   CREATE DATABASE kot_cloud;
   ```

3. Import the system schema and RBAC seed data. The script drops and recreates its system tables, so do not run it against a database containing data that must be preserved.

   ```powershell
   psql -h 127.0.0.1 -p 5432 -U postgres -d kot_cloud -f .\sql\kot_cloud_system.sql
   ```

4. Start Redis and make it reachable at the configured `REDIS_URI`.

5. Configure runtime secrets and connections. The following PowerShell example uses the default local ports.

   ```powershell
   $env:JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/kot_cloud"
   $env:JDBC_USER = "postgres"
   $env:JDBC_PASSWORD = "replace-with-a-database-password"
   $env:REDIS_URI = "redis://127.0.0.1:6379/0"
   $env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"
   ```

`kot-server/src/main/resources/application-local.properties` is intentionally excluded from the setup instructions because it contains machine-specific connection settings. Prefer environment variables for local, CI, and production environments.

## Run

Use the Gradle Wrapper from the repository root. On this Windows workspace, keep Gradle caches outside the system drive:

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-server:run
```

The default port is `1164`. The service is available at `http://localhost:1164`.

For a packaged distribution:

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-server:installDist
.\kot-server\build\install\kot-server\bin\kot-server.bat
```

## Build and Verification

Compile the system module:

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-module-system:kot-module-system-server:compileKotlin
```

Build the aggregate application:

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-server:build
```

`ksp.incremental=false` is intentional in `gradle.properties`; it works around the Micronaut OpenAPI KSP aggregation issue currently affecting incremental PSI output.

## Authentication and Authorization

- Anonymous endpoints include administrator registration and login.
- Registration assigns the editable `system_admin` role.
- Protected endpoints require `Authorization: Bearer <accessToken>`.
- `super_admin` is initialized and maintained directly by database operators. Business APIs do not create, modify, assign, revoke, or delete that role.
- The schema includes `tenant_id` fields as preparation, but the current runtime behavior is single-tenant.

The actual route prefix is versioned, for example `/v1/admin-api`. Consult the generated OpenAPI document for the complete endpoint catalog and request schemas.

## OpenAPI and Apifox

OpenAPI documents are generated during resource processing. Generate the Apifox document explicitly when needed:

```powershell
$env:GRADLE_USER_HOME = "D:\IDEA\.gradle"
.\gradlew.bat :kot-module-system:kot-module-system-server:generateApifoxOpenApi
```

After the application starts, the documents are served at:

- Full runtime-route document: `http://localhost:1164/swagger/kot-cloud-api-1.0.0.yml`
- Apifox import document: `http://localhost:1164/swagger/kot-cloud-apifox.yml`

The Apifox document keeps the server URL but removes `/v{n}/admin-api` and `/v{n}/app-api` only from top-level OpenAPI path keys. Import it as OpenAPI 3.0 YAML; do not convert it to OpenAPI 3.1.

## Deployment Notes

`docker/Dockerfile` packages the `kot-server` distribution archive, and `docker/k8s` contains Kustomize overlays. The current `docker-compose.yml` is an application-container skeleton, not a full PostgreSQL and Redis development stack. Supply database, Redis, JWT, and port settings before using it for a deployment.

## Contributing

1. Create a feature branch.
2. Keep DDD dependencies in the documented direction.
3. Run the focused compilation or build command for the changed module.
4. Submit a pull request with the relevant verification results.
