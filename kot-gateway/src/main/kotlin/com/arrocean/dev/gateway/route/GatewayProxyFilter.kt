package com.arrocean.dev.gateway.route

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.filter.ServerFilterPhase
import org.reactivestreams.Publisher
import java.util.UUID

/**
 * 将匹配 Gateway 路由的请求转发至下游服务。
 *
 * 保留 Authorization 以供下游独立验签，清除 hop-by-hop 和客户端伪造的转发头，
 * 并透传下游的状态码、响应头和响应体。
 */
@Filter("/**")
class GatewayProxyFilter(
    private val routeFactory: GatewayRouteFactory,
    private val httpClient: ProxyHttpClient,
) : HttpServerFilter {

    /** 返回代理过滤器顺序，使认证完成后才向下游发出请求。 */
    override fun getOrder(): Int = ServerFilterPhase.LAST.order() - 100

    /**
     * 代理命中路由的请求，未命中时交还后续过滤链处理。
     *
     * 下游 HTTP 状态异常会被转换为正常响应透传，连接类异常则继续传播给全局异常处理器。
     *
     * @param request 当前入站请求
     * @param chain 后续过滤链
     * @return 下游响应或未匹配路由的后续响应发布者
     */
    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
    ): Publisher<MutableHttpResponse<*>> {
        val resolved = routeFactory.resolver.resolve(request.path) ?: return chain.proceed(request)
        val targetUri = GatewayTargetUriBuilder.build(
            baseUri = resolved.route.targetUri,
            downstreamPath = resolved.downstreamPath,
            rawQuery = request.uri.rawQuery,
        )
        // Mutating the original Netty request preserves its unread streaming body.
        val downstreamRequest = request.mutate().uri(targetUri)
        val headers = downstreamRequest.headers.names().toList()
        headers.filter { name ->
            name.lowercase() in HOP_BY_HOP_HEADERS ||
                name.equals(HttpHeaders.HOST, true) || name.equals("X-Request-Id", true) ||
                name.startsWith("X-Forwarded-", true)
        }.forEach { downstreamRequest.headers.remove(it) }
        downstreamRequest.header("X-Request-Id", UUID.randomUUID().toString())
        request.remoteAddress.address?.hostAddress?.let { downstreamRequest.header("X-Forwarded-For", it) }
        downstreamRequest.header("X-Forwarded-Proto", request.uri.scheme ?: "http")
        return httpClient.proxy(downstreamRequest)
    }

    private companion object {
        val HOP_BY_HOP_HEADERS = setOf(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
        )
    }
}
