package com.arrocean.dev.gateway.security

import com.arrocean.dev.gateway.config.GatewayProperties
import com.arrocean.dev.gateway.route.GatewayRouteFactory
import com.arrocean.dev.framework.common.enums.CommonUserTypeEnum
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GatewayAuthenticationFilterTest {

    @Test
    fun `allows configured public paths without authentication`() {
        val filter = filter()

        val response = Mono.from<io.micronaut.http.MutableHttpResponse<*>>(
            filter.doFilter(HttpRequest.GET<Any>("/v1/admin-api/system/auth/login"), chain())
        ).block()

        assertEquals(HttpStatus.NO_CONTENT, response?.status)
    }

    @Test
    fun `allows CORS preflight requests without authentication`() {
        val filter = filter()

        val response = Mono.from<io.micronaut.http.MutableHttpResponse<*>>(
            filter.doFilter(HttpRequest.OPTIONS<Any>("/admin-api/system/users"), chain())
        ).block()

        assertEquals(HttpStatus.NO_CONTENT, response?.status)
    }

    @Test
    fun `rejects requests with no bearer token`() {
        val filter = filter()

        val response = Mono.from<io.micronaut.http.MutableHttpResponse<*>>(
            filter.doFilter(HttpRequest.GET<Any>("/v1/admin-api/system/users"), chain())
        ).block()

        assertEquals(HttpStatus.UNAUTHORIZED, response?.status)
    }

    @Test
    fun `propagates Redis unavailability for global error mapping`() {
        val filter = GatewayAuthenticationFilter(
            properties = propertiesWithRoutes(),
            authenticationService = GatewayAuthenticationService(
                tokenVerifier = GatewayTokenVerifier { GatewayPrincipal(it, "session-1", CommonUserTypeEnum.USER) },
                sessionValidator = GatewaySessionValidator { throw GatewaySessionUnavailableException(IllegalStateException()) },
            ),
            gatewayRouteFactory = GatewayRouteFactory(propertiesWithRoutes()),
        )

        assertFailsWith<GatewaySessionUnavailableException> {
            Mono.from<io.micronaut.http.MutableHttpResponse<*>>(
                filter.doFilter(HttpRequest.GET<Any>("/v1/admin-api/system/users").header("Authorization", "Bearer token"), chain())
            ).block()
        }
    }

    private fun filter(): GatewayAuthenticationFilter {
        return GatewayAuthenticationFilter(
            properties = propertiesWithRoutes(),
            authenticationService = GatewayAuthenticationService(
                tokenVerifier = GatewayTokenVerifier { GatewayPrincipal(it, "session-1", CommonUserTypeEnum.USER) },
                sessionValidator = GatewaySessionValidator { true },
            ),
            gatewayRouteFactory = GatewayRouteFactory(propertiesWithRoutes()),
        )
    }

    @Test
    fun `rejects a member token at the admin entry point`() {
        val filter = filter()

        assertFailsWith<GatewayIdentityVerificationException> {
            Mono.from<io.micronaut.http.MutableHttpResponse<*>>(
                filter.doFilter(
                    HttpRequest.GET<Any>("/v1/admin-api/system/users").header("Authorization", "Bearer token"),
                    chain(),
                )
            ).block()
        }
    }

    @Test
    fun `rejects an admin token at the public api entry point`() {
        val filter = GatewayAuthenticationFilter(
            properties = propertiesWithRoutes(),
            authenticationService = GatewayAuthenticationService(
                tokenVerifier = GatewayTokenVerifier { GatewayPrincipal(it, "session-1", CommonUserTypeEnum.ADMIN) },
                sessionValidator = GatewaySessionValidator { true },
            ),
            gatewayRouteFactory = GatewayRouteFactory(propertiesWithRoutes()),
        )

        assertFailsWith<GatewayIdentityVerificationException> {
            Mono.from<io.micronaut.http.MutableHttpResponse<*>>(
                filter.doFilter(
                    HttpRequest.GET<Any>("/v1/api/user/me").header("Authorization", "Bearer token"),
                    chain(),
                )
            ).block()
        }
    }

    @Test
    fun `rejects a request outside configured entry points`() {
        val filter = filter()

        val response = Mono.from<io.micronaut.http.MutableHttpResponse<*>>(
            filter.doFilter(HttpRequest.GET<Any>("/v1/unknown/resource"), chain())
        ).block()

        assertEquals(HttpStatus.NOT_FOUND, response?.status)
    }

    private fun propertiesWithRoutes(): GatewayProperties {
        return GatewayProperties().apply {
            publicPaths = listOf("/v1/admin-api/system/auth/login")
            routes = listOf(
                GatewayProperties.RouteProperties().apply {
                    id = "system"
                    pathPrefix = "/v1/admin-api/system"
                    targetUri = "http://system.internal"
                    allowedUserTypes = listOf(CommonUserTypeEnum.ADMIN.name)
                },
                GatewayProperties.RouteProperties().apply {
                    id = "api"
                    pathPrefix = "/v1/api"
                    targetUri = "http://api.internal"
                    allowedUserTypes = listOf(CommonUserTypeEnum.USER.name)
                },
            )
        }
    }

    private fun chain(): io.micronaut.http.filter.ServerFilterChain =
        io.micronaut.http.filter.ServerFilterChain { _: HttpRequest<*> ->
            Mono.just<io.micronaut.http.MutableHttpResponse<*>>(io.micronaut.http.HttpResponse.noContent<Any>())
        }
}
