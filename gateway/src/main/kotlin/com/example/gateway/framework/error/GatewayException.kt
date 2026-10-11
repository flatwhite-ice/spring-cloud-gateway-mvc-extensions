package com.example.gateway.framework.error

/**
 * Thrown by a route filter to return [errorCode] to the caller.
 *
 * A filter can also build a short-circuit `ServerResponse` itself, but throwing lets every route
 * share one error body: [GatewayExceptionHandler] renders it. Throwing short-circuits just as
 * returning a response does — the downstream is never called.
 *
 * ```
 * val userId = introspector.resolveUserId(header)
 *     ?: throw GatewayException(RouteErrorCode.NOT_FOUND_TOKEN)
 * ```
 */
open class GatewayException(
    val errorCode: GatewayErrorCode,
    cause: Throwable? = null,
) : RuntimeException(errorCode.message, cause)
