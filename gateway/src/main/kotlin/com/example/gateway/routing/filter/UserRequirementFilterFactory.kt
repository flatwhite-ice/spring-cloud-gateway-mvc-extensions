package com.example.gateway.routing.filter

import com.example.gateway.framework.filter.trace.FilterTrace
import com.example.gateway.routing.filter.AuthFilter.Companion.USER_ID_HEADER
import com.example.gateway.security.TokenIntrospector
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

/**
 * Builds a filter whose behaviour depends on a per-route argument.
 *
 * `@GatewayFilterBean` covers filters that take no arguments, which is almost all of them. A filter
 * that needs different settings per route cannot be a single bean, so it becomes a factory bean
 * instead — the factory takes constructor injection, and the hand-written alias in
 * [GatewayArgsFilterAliases] passes the route's arguments through to [create].
 */
@Component
class UserRequirementFilterFactory(private val introspector: TokenIntrospector) {

    /**
     * @param mode `strict` rejects an unauthenticated request with 401, `lenient` lets it through
     * as `anonymous`.
     */
    fun create(mode: String): HandlerFilterFunction<ServerResponse, ServerResponse> {
        val strict = when (mode) {
            "strict" -> true
            "lenient" -> false
            // Route arguments are bound at startup, so a typo fails before the port opens.
            else -> throw IllegalArgumentException("requireUser mode must be 'strict' or 'lenient', was '$mode'")
        }
        return HandlerFilterFunction { request, next ->
            val userId = introspector.resolveUserId(request.headers().firstHeader(HttpHeaders.AUTHORIZATION))
            when {
                userId != null -> next.handle(withUser(request, userId))
                strict -> {
                    logger.info("requireUser(strict): rejecting {}", request.path())
                    ServerResponse.status(HttpStatus.UNAUTHORIZED).build()
                }
                else -> {
                    logger.info("requireUser(lenient): passing {} through as anonymous", request.path())
                    next.handle(withUser(request, "anonymous"))
                }
            }
        }
    }

    private fun withUser(request: ServerRequest, userId: String): ServerRequest =
        ServerRequest.from(request)
            .headers { headers ->
                headers[USER_ID_HEADER] = userId
                FilterTrace.append(headers, request, "requireUser")
            }
            .build()

    private companion object {
        private val logger = LoggerFactory.getLogger(UserRequirementFilterFactory::class.java)
    }
}
