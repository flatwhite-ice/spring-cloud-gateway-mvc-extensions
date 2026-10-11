package com.example.gateway.framework.error

import org.springframework.http.HttpStatus

/**
 * Codes the framework itself returns, for failures no application code asked for.
 */
enum class FrameworkErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : GatewayErrorCode {

    /**
     * Anything that reached [GatewayExceptionHandler] without being a [GatewayException]. The real
     * exception goes to the log, never into the response.
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하는 중 오류가 발생했습니다."),
    ;

    override val code get() = name
}
