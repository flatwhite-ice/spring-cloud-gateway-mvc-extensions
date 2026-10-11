package com.example.gateway.framework.error

import org.springframework.boot.webmvc.error.DefaultErrorAttributes
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.context.request.WebRequest
import org.springframework.boot.web.error.ErrorAttributeOptions

/**
 * Renders everything that reaches `/error` as an [ErrorResponse], so an unexpected failure looks
 * like a deliberate one.
 *
 * Why here rather than an `@ExceptionHandler(Exception::class)` catch-all:
 * `ExceptionHandlerExceptionResolver` walks the `@ControllerAdvice` beans and takes the **first**
 * one with a matching method, and an unordered advice sits at `LOWEST_PRECEDENCE` just like an
 * explicitly last-ordered one — so a catch-all advice shadows application advices unpredictably,
 * depending on bean ordering. Hooking `ErrorAttributes` instead cannot shadow anything: Spring only
 * forwards to `/error` once every resolver has declined.
 *
 * It also covers what an advice cannot: a request that matches no route never throws — Spring calls
 * `sendError` — so this is what makes that 404 carry the same body.
 *
 * Replaces Boot's own bean, which `ErrorMvcAutoConfiguration` declares
 * `@ConditionalOnMissingBean(ErrorAttributes::class)`.
 */
@Component
class GatewayErrorAttributes : DefaultErrorAttributes() {

    override fun getErrorAttributes(webRequest: WebRequest, options: ErrorAttributeOptions): Map<String, Any> {
        val defaults = super.getErrorAttributes(webRequest, options)
        val status = HttpStatus.resolve(defaults["status"] as? Int ?: 500)
        return ErrorResponse(
            errorCode = status?.name ?: FrameworkErrorCode.INTERNAL_ERROR.code,
            // 5xx messages are generic on purpose: the real exception is already in the log and
            // must not reach the caller.
            errorMessage = when {
                status == null || status.is5xxServerError -> FrameworkErrorCode.INTERNAL_ERROR.message
                else -> status.reasonPhrase
            },
        ).let { mapOf("error_code" to it.errorCode, "error_message" to it.errorMessage) }
    }
}
