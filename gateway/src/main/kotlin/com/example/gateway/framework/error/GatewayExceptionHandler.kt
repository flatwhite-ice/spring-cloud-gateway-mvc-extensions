package com.example.gateway.framework.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Turns a [GatewayException] into the [ErrorResponse] its error code describes, so routes do not
 * each invent their own error body.
 *
 * **No selectors on `@ControllerAdvice`, and that is not a style choice.**
 * `ExceptionHandlerExceptionResolver` tests `basePackages` / `assignableTypes` / `annotations`
 * against the handler's type, and a gateway route's handler is a `HandlerFunction`, not a
 * `HandlerMethod` — there is no type to test, so a scoped advice silently never matches and the
 * error falls through to Spring's default. `GatewayFilterErrorHandlingTests` pins that down.
 *
 * Handles exactly one exception type. Everything else is left alone: a catch-all
 * `@ExceptionHandler(Exception::class)` here would shadow application advices, because the resolver
 * takes the first advice with a matching method and an unordered advice already sits at
 * `LOWEST_PRECEDENCE`. [GatewayErrorAttributes] covers the unexpected cases instead, from a
 * position where it cannot shadow anything.
 *
 * Picked up by the ordinary component scan: `@ControllerAdvice` is meta-annotated `@Component`.
 */
@ControllerAdvice
class GatewayExceptionHandler {

    @ExceptionHandler(GatewayException::class)
    fun handle(ex: GatewayException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errorCode = ex.errorCode
        logger.warn("{} {} -> {} {}", request.method, request.requestURI, errorCode.status.value(), errorCode.code)
        return ResponseEntity.status(errorCode.status).body(ErrorResponse(errorCode))
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(GatewayExceptionHandler::class.java)
    }
}
