package com.example.gateway.framework.filter.delegate

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

/**
 * Resolves the real filter from the application context on first use and caches it.
 *
 * Gateway Server Web MVC invokes a *static method* to build a route's filter, and static methods
 * cannot take constructor injection. This bridges the gap: the static alias returns one of these,
 * and it looks the bean up from the `ApplicationContext` carried on the request.
 *
 * Caching is safe across route refreshes: `RouterFunctionHolderFactory` rebuilds the routes by
 * calling the alias methods again, which produces fresh instances and discards the previously
 * cached delegates. Each route gets its own instance, so a filter named by two routes is resolved
 * once per route.
 */
internal class BeanDelegatingFilter(val target: BeanTarget) :
    HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Volatile
    private var delegate: HandlerFilterFunction<ServerResponse, ServerResponse>? = null

    @Volatile
    private var name: String = target.label

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): ServerResponse {
        var resolved = delegate
        if (resolved == null) {
            // Racing threads may look the bean up more than once; that is harmless because the bean
            // is a singleton and the lookup is idempotent.
            val context = MvcUtils.getApplicationContext(request)
            resolved = target.resolve(context)
            name = target.describe(context)
            delegate = resolved
            logger.debug("resolved '{}' to {}", name, resolved.javaClass.name)
        }
        // Traced here rather than in each filter so every bean-delegated filter reports the same
        // way, and the nesting of the lines is the YAML filter order.
        logger.debug("--> {} {} {}", name, request.method(), request.path())
        try {
            val response = resolved.filter(request, next)
            logger.debug("<-- {} [{}]", name, response.statusCode().value())
            return response
        } catch (ex: Exception) {
            logger.debug("<-- {} threw {}", name, ex.javaClass.simpleName)
            throw ex
        }
    }

    override fun toString(): String = "BeanDelegatingFilter[$name]"

    private companion object {
        private val logger = LoggerFactory.getLogger(BeanDelegatingFilter::class.java)
    }
}
