package com.example.gateway.routing.filter

import com.example.gateway.framework.filter.GatewayFilterAlias
import com.example.gateway.framework.filter.GatewayFilterBean
import com.example.gateway.framework.error.GatewayException
import com.example.gateway.framework.filter.beanFilter
import com.example.gateway.framework.filter.trace.FilterTrace
import com.example.gateway.routing.error.RouteErrorCode
import com.example.gateway.security.TokenIntrospector
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

/**
 * Resolves the caller from the `Authorization` header and puts the user id on the request for
 * downstream services. Short-circuits with 401 when the token is missing or invalid, so the
 * downstream is never called.
 *
 * Referenced from YAML as `- name: authFilter`, which is the bean name `@GatewayFilterBean` derives
 * from the class name.
 */
@GatewayFilterBean
class AuthFilter(private val introspector: TokenIntrospector) :
    HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): ServerResponse {
        // Throwing rather than returning a short-circuit response: GatewayExceptionHandler renders
        // the body, so every route reports an auth failure the same way. The downstream is not
        // called either way.
        val userId = introspector.resolveUserId(request.headers().firstHeader(HttpHeaders.AUTHORIZATION))
            ?: throw GatewayException(RouteErrorCode.NOT_FOUND_TOKEN)
        logger.info("authFilter: authenticated userId={}, setting {}", userId, USER_ID_HEADER)
        val modified = ServerRequest.from(request)
            // headers { it.set(..) } replaces; ServerRequest.Builder.header(..) appends, which
            // would let a client-supplied X-User-Id ride along next to ours.
            .headers { headers ->
                headers[USER_ID_HEADER] = userId
                FilterTrace.append(headers, request, "authFilter")
            }
            .build()
        return next.handle(modified)
    }

    companion object : GatewayFilterAlias {

        /** Header carrying the authenticated user id. */
        const val USER_ID_HEADER = "X-User-Id"

        private val logger = LoggerFactory.getLogger(AuthFilter::class.java)

        @JvmStatic
        override fun alias() = beanFilter<AuthFilter>()
    }
}
