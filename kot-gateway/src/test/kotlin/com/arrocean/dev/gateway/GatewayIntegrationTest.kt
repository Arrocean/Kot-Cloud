package com.arrocean.dev.gateway

import com.arrocean.dev.gateway.config.GatewayProperties
import com.arrocean.dev.gateway.route.GatewayRouteFactory
import com.arrocean.dev.gateway.security.GatewaySessionValidator
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatewayIntegrationTest {
    @Test
    fun `application configuration binds nested properties and indexed routes`() {
        ApplicationContext.run().use { context ->
            val properties = context.getBean(GatewayProperties::class.java)
            assertEquals(setOf("system", "member"), properties.routes.map { it.id }.toSet())
            val system = properties.routes.single { it.id == "system" }
            assertEquals("http://127.0.0.1:1164", system.targetUri)
            assertEquals(listOf("ADMIN"), system.allowedUserTypes)
            assertTrue(properties.jwt.secret.length >= 32)
            assertEquals("kot:security", properties.redis.keyPrefix)
            assertEquals(listOf("http://localhost:3000"), properties.cors.allowedOrigins)
            assertEquals("redis://127.0.0.1:6379/0", context.environment.getProperty("redis.uri", String::class.java).get())
            assertFalse(properties.publicPaths.contains("/health"))
            assertTrue(properties.publicPaths.contains("/v1/admin-api/system/auth/login"))
            assertEquals("system", context.getBean(GatewayRouteFactory::class.java).resolver
                .resolve("/v1/admin-api/system/user/profile")?.route?.id)
        }
    }

    @Test
    fun `real HTTP filter chain authenticates before proxy and preserves signed identity`() {
        val hits = AtomicInteger()
        val secret = "isolated-integration-test-secret-at-least-32-bytes"
        val downstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        downstream.createContext("/") { exchange ->
            hits.incrementAndGet()
            val body = if (exchange.requestMethod == "POST") exchange.requestBody.readAllBytes() else
                (exchange.requestURI.toString() + "|" + exchange.requestHeaders.getFirst("Authorization").orEmpty()).toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(if (exchange.requestURI.query == "probe=error") 422 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        downstream.start()
        val context = ApplicationContext.builder().properties(mapOf(
            "micronaut.server.port" to -1,
            "kot.gateway.routes[0].target-uri" to "http://127.0.0.1:${downstream.address.port}",
            "kot.gateway.jwt.secret" to secret,
        )).build()
        context.registerSingleton(GatewaySessionValidator::class.java, GatewaySessionValidator { it == "active-test-session" })
        try {
            context.start()
            val server = context.getBean(EmbeddedServer::class.java).start()
            val client = HttpClient.newHttpClient()
            fun request(path: String, token: String? = null): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path"))
                token?.let { builder.header("Authorization", "Bearer $it") }
                return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
            }
            fun token(userType: Int = 1, session: String = "active-test-session", expires: Long = 60): String {
                val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder()
                    .subject("integration-user").claim("userType", userType).claim("sessionId", session)
                    .expirationTime(Date.from(Instant.now().plusSeconds(expires))).build())
                jwt.sign(MACSigner(secret))
                return jwt.serialize()
            }
            val public = request("/v1/admin-api/system/auth/login?probe=public")
            assertEquals(200, public.statusCode())
            assertEquals("/v1/admin-api/system/auth/login?probe=public|", public.body())
            assertEquals(401, request("/v1/admin-api/system/user/profile").statusCode())
            assertEquals(401, request("/v1/admin-api/system/user/profile", "invalid").statusCode())
            assertEquals(401, request("/v1/admin-api/system/user/profile", token(expires = -1)).statusCode())
            assertEquals(401, request("/v1/admin-api/system/user/profile", token(session = "revoked")).statusCode())
            assertEquals(403, request("/v1/admin-api/system/user/profile", token(userType = 2)).statusCode())
            assertEquals(1, hits.get())
            val valid = token()
            val protected = request("/v1/admin-api/system/user/profile?probe=authenticated", valid)
            assertEquals(200, protected.statusCode())
            assertEquals("/v1/admin-api/system/user/profile?probe=authenticated|Bearer $valid", protected.body())
            assertEquals(2, hits.get())
            val downstreamError = request("/v1/admin-api/system/auth/login?probe=error")
            assertEquals(422, downstreamError.statusCode())
            assertEquals("/v1/admin-api/system/auth/login?probe=error|", downstreamError.body())
            val payload = "{\"username\":\"isolated\",\"password\":\"test-only\"}"
            val posted = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/admin-api/system/auth/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                HttpResponse.BodyHandlers.ofString())
            assertEquals(200, posted.statusCode())
            assertEquals(payload, posted.body())
            assertEquals(404, request("/unmapped").statusCode())
        } finally {
            context.close()
            downstream.stop(0)
        }
    }
}
