plugins {
    application
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

group = "com.arrocean.dev"
version = "0.0.1"

application {
    mainClass.set("com.arrocean.dev.module.system.SystemServerApplication")
}

dependencies {
    // Micronaut HTTP
    api(libs.micronaut.http.client)
    api(libs.micronaut.http.server.netty)

    // Kotlin Logging API
    implementation(libs.kotlin.logging)

    // Micronaut Validation
    api(libs.micronaut.validation)
    ksp(libs.micronaut.validation.processor)

    // Micronaut Data
    api(libs.micronaut.data.runtime)
    api(libs.micronaut.data.jdbc)
    api(libs.micronaut.data.tx)

    // Micronaut Data Processor
    ksp(libs.micronaut.data.processor)

    // Micronaut Jackson
    api(libs.micronaut.serde.jackson)
    api(libs.micronaut.jackson.databind)

    // JDBC
    api(libs.micronaut.jdbc.hikari)

    // Postgre SQL
    runtimeOnly(libs.postgresql)

    // Micronaut 注解处理器
    ksp(libs.micronaut.inject.kotlin)

    // Micronaut OpenApi
    ksp(libs.micronaut.openapi)
    api(libs.micronaut.openapi.annotations)

    // system-api 模块
    api(project(":kot-module-system:kot-module-system-api"))
    // kot-common-api 模块
    api(project(":kot-framework:kot-common-api"))
    // kot-common-model 模块
    api(project(":kot-framework:kot-common-model"))

    // Starter
    // starter-web 模块
    api(project(":kot-framework:kot-micronaut-starter-web"))
    // starter-security 模块
    api(project(":kot-framework:kot-micronaut-starter-security"))
    // starter-md-core 模块
    api(project(":kot-framework:kot-micronaut-starter-md-core"))
    // starter-md-postgresql 模块
    api(project(":kot-framework:kot-micronaut-starter-md-postgresql"))
}

/**
 * 在每个路径中生成不带运行时API前缀的Apifox导入文档。
 *
 * 原始 OpenAPI 文件保持不变。该任务支持剥离任意版本的管理端和前台前缀，
 * 例如 `/v1/admin-api`、`/v1/app-api`、`/v2/admin-api`。服务器 URL 不会被修改，
 * 因此前端 URL 仍然解析到实际运行时端点。
 */
tasks.register("generateApifoxOpenApi") {
    description = "在每个路径中生成不带运行时API前缀的Apifox导入文档"
    dependsOn("kspKotlin")

    doLast {
        val source = layout.buildDirectory.file(
            "generated/ksp/main/resources/META-INF/swagger/kot-cloud-api-1.0.0.yml",
        ).get().asFile
        check(source.isFile) { "OpenAPI source file was not generated: ${source.absolutePath}" }

        val output = layout.buildDirectory.file(
            "openapi/kot-cloud-apifox.yml",
        ).get().asFile
        output.parentFile.mkdirs()

        val swaggerOutput = layout.buildDirectory.file(
            "generated/ksp/main/resources/META-INF/swagger/kot-cloud-apifox.yml",
        ).get().asFile
        swaggerOutput.parentFile.mkdirs()

        // Only rewrite top-level OpenAPI path keys. Do not alter server URLs or example payloads.
        val apiPrefixAtPathKey = Regex("(?m)^(  )/v\\d+/(?:admin-api|app-api)(?=/)")
        val apifoxDocument = source.readText().replace(apiPrefixAtPathKey, "$1")
        output.writeText(apifoxDocument)
        swaggerOutput.writeText(apifoxDocument)
    }
}

tasks.named("processResources") {
    dependsOn("generateApifoxOpenApi")
}
