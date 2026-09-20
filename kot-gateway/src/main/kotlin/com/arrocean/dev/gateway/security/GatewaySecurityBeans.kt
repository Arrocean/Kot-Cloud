package com.arrocean.dev.gateway.security

import com.arrocean.dev.gateway.config.GatewayProperties
import com.arrocean.dev.framework.common.enums.toCommonUserTypeEnum
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.SignedJWT
import io.lettuce.core.api.StatefulRedisConnection
import jakarta.inject.Singleton
import jakarta.inject.Provider
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

/**
 * 基于 Nimbus 的 HS256 JWT 校验器。
 *
 * 校验签名、有效期、可选 issuer/audience 和必需的 sessionId claim。
 */
@Singleton
class NimbusGatewayTokenVerifier(
    private val properties: GatewayProperties,
) : GatewayTokenVerifier {

    /**
     * 验证 HS256 JWT 并提取 sessionId。
     *
     * @param token 不含 Bearer 前缀的 JWT 字符串
     * @return 包含原始 Token 和会话标识的认证主体
     * @throws GatewayAuthenticationException Token 无法解析、签名或 claims 校验失败时抛出
     */
    override fun verify(token: String): GatewayPrincipal {
        val signedJwt = try {
            SignedJWT.parse(token)
        } catch (ex: Exception) {
            throw GatewayAuthenticationException("Malformed JWT", ex)
        }
        if (signedJwt.header.algorithm != JWSAlgorithm.HS256 || !verifySignature(signedJwt)) {
            throw GatewayAuthenticationException("Invalid JWT signature")
        }
        val claims = signedJwt.jwtClaimsSet
        val now = Date.from(Instant.now())
        if (claims.expirationTime == null || !claims.expirationTime.after(now)) {
            throw GatewayAuthenticationException("Expired JWT")
        }
        claims.notBeforeTime?.let {
            if (it.after(now)) {
                throw GatewayAuthenticationException("JWT is not active")
            }
        }
        properties.jwt.issuer?.trim()?.takeIf(String::isNotBlank)?.let {
            if (claims.issuer != it) throw GatewayAuthenticationException("Unexpected JWT issuer")
        }
        properties.jwt.audience?.trim()?.takeIf(String::isNotBlank)?.let {
            if (!claims.audience.contains(it)) throw GatewayAuthenticationException("Unexpected JWT audience")
        }
        val sessionId = claims.getStringClaim("sessionId")?.trim()?.takeIf { it.isNotBlank() }
            ?: throw GatewayAuthenticationException("JWT session id is missing")
        val userType = (claims.getClaim("userType") as? Number)?.toInt()?.toCommonUserTypeEnum()
            ?: throw GatewayAuthenticationException("JWT user type is missing or invalid")
        return GatewayPrincipal(token, sessionId, userType)
    }

    /**
     * 使用配置中的共享密钥校验 JWT 签名。
     *
     * @param signedJwt 已解析但尚未验证的 JWT
     * @return 签名有效时返回 true
     * @throws GatewayAuthenticationException 密钥长度不符合 HS256 要求或校验器执行失败时抛出
     */
    private fun verifySignature(signedJwt: SignedJWT): Boolean {
        val secret = properties.jwt.secret.toByteArray(StandardCharsets.UTF_8)
        if (secret.size < 32) {
            throw GatewayAuthenticationException("JWT secret must contain at least 32 bytes")
        }
        return try {
            signedJwt.verify(MACVerifier(secret))
        } catch (ex: Exception) {
            throw GatewayAuthenticationException("JWT signature validation failed", ex)
        }
    }
}

/** 通过 Redis 会话记录确认已验签 Token 尚未被撤销。 */
@Singleton
class RedisGatewaySessionValidator(
    private val redisConnection: Provider<StatefulRedisConnection<String, String>>,
    private val properties: GatewayProperties,
) : GatewaySessionValidator {

    /**
     * 查询 Redis 会话记录是否仍存在。
     *
     * @param sessionId 已通过 JWT 验签的会话标识
     * @return 会话 Key 存在时返回 true
     * @throws GatewaySessionUnavailableException Redis 连接或命令执行失败时抛出
     */
    override fun isActive(sessionId: String): Boolean {
        return try {
            redisConnection.get().sync().exists(GatewaySessionKeyFactory.sessionKey(properties.redis.keyPrefix, sessionId)) > 0
        } catch (ex: Exception) {
            throw GatewaySessionUnavailableException(ex)
        }
    }
}

/** Redis 无法完成会话校验时抛出的网关基础设施异常。 */
class GatewaySessionUnavailableException(cause: Throwable) : RuntimeException("Redis session validation failed", cause)
