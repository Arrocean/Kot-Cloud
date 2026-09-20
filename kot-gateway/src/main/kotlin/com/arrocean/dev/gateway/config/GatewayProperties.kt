package com.arrocean.dev.gateway.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty

/**
 * Gateway 的运行时配置。
 *
 * 路由、认证会话和跨域策略均通过配置提供，避免网关与具体下游业务模块耦合。
 */
@ConfigurationProperties("kot.gateway")
open class GatewayProperties @jakarta.inject.Inject constructor(
    routes: List<RouteProperties> = emptyList(),
) {

    var routes: List<RouteProperties> = routes
    var publicPaths: List<String> = emptyList()
    var jwt: JwtProperties = JwtProperties()
    var redis: RedisProperties = RedisProperties()
    var cors: CorsProperties = CorsProperties()

    /** 单条下游路由配置。 */
    @EachProperty("routes", list = true)
    open class RouteProperties {
        var id: String = ""
        var pathPrefix: String = ""
        var targetUri: String = ""
        var stripPrefix: Boolean = false
        var enabled: Boolean = true
        var allowedUserTypes: List<String> = emptyList()
    }

    /** Redis 会话校验配置。 */
    @ConfigurationProperties("redis")
    open class RedisProperties {
        var keyPrefix: String = "kot:security"
    }

    /** JWT 验签配置。 */
    @ConfigurationProperties("jwt")
    open class JwtProperties {
        var secret: String = ""
        var issuer: String? = null
        var audience: String? = null
    }

    /** 跨域响应策略。 */
    @ConfigurationProperties("cors")
    open class CorsProperties {
        var allowedOrigins: List<String> = emptyList()
        var allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        var allowedHeaders: List<String> = listOf("Authorization", "Content-Type", "X-Request-Id")
        var exposedHeaders: List<String> = listOf("X-Request-Id")
        var allowCredentials: Boolean = false
        var maxAgeSeconds: Long = 3600
    }
}
