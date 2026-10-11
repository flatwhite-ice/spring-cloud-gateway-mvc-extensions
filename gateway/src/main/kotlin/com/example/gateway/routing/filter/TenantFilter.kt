package com.example.gateway.routing.filter

import com.example.gateway.framework.filter.GatewayFilterAlias
import com.example.gateway.framework.filter.GatewayFilterBean
import com.example.gateway.framework.filter.beanFilter
import com.example.gateway.framework.filter.trace.FilterTrace
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

/**
 * Derives the tenant from the user id that [AuthFilter] put on the request, so it only works when
 * it runs after `authFilter`. That dependency is deliberate: it makes the YAML filter order
 * observable.
 */
@GatewayFilterBean
class TenantFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): ServerResponse {
        val userId = request.headers().firstHeader(AuthFilter.USER_ID_HEADER)
        if (userId == null) {
            // authFilter did not run before us: fail loudly rather than forward a request with no
            // tenant.
            logger.error(
                "tenantFilter: no {} on the request, so authFilter did not run first",
                AuthFilter.USER_ID_HEADER,
            )
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        val tenantId = "tenant-$userId"
        logger.info("tenantFilter: userId={} -> {}={}", userId, TENANT_ID_HEADER, tenantId)
        val modified = ServerRequest.from(request)
            .headers { headers ->
                headers[TENANT_ID_HEADER] = tenantId
                FilterTrace.append(headers, request, "tenantFilter")
            }
            .build()
        return next.handle(modified)
    }

    companion object : GatewayFilterAlias {

        /** Header carrying the resolved tenant id. */
        const val TENANT_ID_HEADER = "X-Tenant-Id"

        private val logger = LoggerFactory.getLogger(TenantFilter::class.java)

        @JvmStatic
        override fun alias() = beanFilter<TenantFilter>()
    }
}
