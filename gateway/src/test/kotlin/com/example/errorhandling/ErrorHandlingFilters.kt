package com.example.errorhandling

import com.example.gateway.framework.filter.GatewayFilterAlias
import com.example.gateway.framework.filter.GatewayFilterBean
import com.example.gateway.framework.filter.beanFilter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

// Fixtures for GatewayFilterErrorHandlingTests.
//
// Outside `com.example.gateway` so the component scan does not pick these up — a `@ControllerAdvice`
// leaking into every other test's context would change their error responses.

class UnauthorizedTokenException(message: String) : RuntimeException(message)

class ScopedException(message: String) : RuntimeException(message)

/** Signals the failure by throwing, instead of returning a short-circuit ServerResponse. */
@GatewayFilterBean
class ThrowingAuthFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): ServerResponse =
        throw UnauthorizedTokenException("no token")

    companion object : GatewayFilterAlias {
        @JvmStatic override fun alias() = beanFilter<ThrowingAuthFilter>()
    }
}

/** `ResponseStatusException` needs no `@ControllerAdvice` at all. */
@GatewayFilterBean
class PaymentRequiredFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): ServerResponse =
        throw ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "pay up")

    companion object : GatewayFilterAlias {
        @JvmStatic override fun alias() = beanFilter<PaymentRequiredFilter>()
    }
}

/** Throws something only a selector-scoped advice declares a handler for. */
@GatewayFilterBean
class ScopedAdviceFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): ServerResponse =
        throw ScopedException("scoped")

    companion object : GatewayFilterAlias {
        @JvmStatic override fun alias() = beanFilter<ScopedAdviceFilter>()
    }
}

/** No selectors, so it applies to gateway routes. */
@ControllerAdvice
class GlobalFilterExceptionHandler {

    @ExceptionHandler(UnauthorizedTokenException::class)
    fun handle(ex: UnauthorizedTokenException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("error" to "unauthorized", "detail" to ex.message))
}

/**
 * Scoped by package. A gateway route's handler is a `HandlerFunction`, not a `HandlerMethod`, so
 * there is no handler type for the selector to test and this never matches.
 */
@ControllerAdvice(basePackages = ["com.example.errorhandling"])
class ScopedFilterExceptionHandler {

    @ExceptionHandler(ScopedException::class)
    fun handle(ex: ScopedException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT)
            .body(mapOf("error" to "scoped-advice-ran", "detail" to ex.message))
}
