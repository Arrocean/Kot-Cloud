# Contributing to kot-cloud

Thank you for your interest in contributing to `kot-cloud`! This document explains the contributor license agreement, the contribution workflow, pull request requirements, and how to set up a development environment.

## 1. Contributor License Agreement (CLA)

By submitting a pull request, patch, issue comment, or any other contribution to this repository, you agree to the following terms:

1. You grant the project owner (Arrocean) and all recipients of the software a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable copyright and patent license to use, reproduce, prepare derivative works of, publicly display, sublicense, and distribute your contribution as part of `kot-cloud` under its existing [MIT license](LICENSE) or any other license the project owner may adopt in the future.
2. You confirm that your contribution is your own original work (or that you have the right to submit it) and that you are legally allowed to grant the license above.
3. Contributions are provided "as is", without warranties or conditions of any kind.

**No signature is required. Opening a pull request is deemed full acceptance of this agreement.** If you do not agree with these terms, please do not submit a contribution.

## 2. How to Contribute

All contributions must go through the fork workflow. Direct pushes to this repository are not accepted.

1. **Fork** this repository to your own account.
2. **Clone your fork** locally:

   ```bash
   git clone https://<forge>/you/kot-cloud.git
   cd kot-cloud
   ```

3. **Create a branch** for your change:

   ```bash
   git checkout -b feat/my-feature
   ```

4. **Make your changes.** Keep each pull request focused on a single change; unrelated changes should go into separate pull requests.
5. **Build and test locally** (see [Development Environment Setup](#4-development-environment-setup)) and make sure `./gradlew build` passes.
6. **Push the branch to your fork** and open a pull request against the `master` branch of this repository.

For larger changes (new modules, architecture changes, protocol changes), please open an issue first to discuss the design before writing code.

## 3. Pull Request Guidelines

Keep it simple. A pull request should have:

**Title format**

```
type(scope): short summary
```

- `type` is one of: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `build`.
- `scope` is optional and names the affected area, e.g. `gateway`, `system`, `member`, `framework`, `server`.
- The summary is a short imperative sentence, e.g. `feat(gateway): add route-level identity check`.

**Description**

- What the change does and why it is needed (one or two sentences are enough for small changes).
- How to verify the change (commands, endpoints, or manual steps).
- A short checklist confirming:

  - [ ] `./gradlew build` passes locally
  - [ ] New behavior is covered by tests where practical
  - [ ] Documentation (`README` / `docs/`) is updated if the change affects it

Maintainers may squash or rebase your commits when merging.

## 4. Development Environment Setup

### Prerequisites

| Tool       | Version               | Notes                                                |
|------------|-----------------------|------------------------------------------------------|
| JDK        | 21 or later           | GraalVM optional, only for `kot-server` native image |
| PostgreSQL | Any supported version | Local instance or remote test database               |
| Redis      | Any supported version | Required for token sessions                          |
| Git        | Latest                |                                                      |

Gradle is invoked through the wrapper (`./gradlew`, or `gradlew.bat` on Windows), so no local Gradle installation is needed. The project uses Kotlin 2.4.20, Gradle 9.7.1, and Micronaut 5.1.5 (Platform BOM).

### Configuration

Services read configuration from `application.properties` in each module's `src/main/resources` and can be overridden with environment variables:

| Variable                                    | Used by                             | Purpose                                                    |
|---------------------------------------------|-------------------------------------|------------------------------------------------------------|
| `JDBC_URL`                                  | `kot-server`, system                | PostgreSQL JDBC URL                                        |
| `JDBC_USER` / `JDBC_PASSWORD`               | `kot-server`, system                | Database credentials                                       |
| `REDIS_URI`                                 | `kot-server`, `kot-gateway`, system | Redis connection URI, e.g. `redis://127.0.0.1:6379/0`      |
| `JWT_SECRET`                                | all services                        | Shared JWT signing secret (at least 32 bytes)              |
| `PASSWORD_ENCODER`                          | `kot-server`, system                | `pbkdf2` (default), `bcrypt`, or `argon2id`                |
| `GATEWAY_PORT`                              | `kot-gateway`                       | Gateway listen port (default `8080`)                       |
| `SYSTEM_SERVICE_URL` / `MEMBER_SERVICE_URL` | `kot-gateway`                       | Downstream route targets (default `http://127.0.0.1:1164`) |

`kot-server` also ships an `application-local` profile with a ready-made local configuration.

### Running

```bash
# Monolith (default port 1164)
./gradlew :kot-server:run

# Standalone system module (profile "system", port 1164)
MICRONAUT_ENVIRONMENTS=system ./gradlew :kot-module-system:kot-module-system-server:run

# Gateway (default port 8080, routes to http://127.0.0.1:1164 by default)
./gradlew :kot-gateway:installDist
./kot-gateway/build/install/kot-gateway/bin/kot-gateway
```

### Testing and building

```bash
./gradlew build          # compile + test all modules
./gradlew test           # tests only
./gradlew :kot-gateway:test
```

If a service starts but behaves unexpectedly, check its startup log first; most runtime failures in local development come from unreachable PostgreSQL or Redis.

## 5. Thanks

Every contribution matters, whether it is a one-line typo fix or a whole new module. Thank you for taking the time to improve `kot-cloud` — happy hacking!
