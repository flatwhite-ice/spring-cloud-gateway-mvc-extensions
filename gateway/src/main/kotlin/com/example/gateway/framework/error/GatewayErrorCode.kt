package com.example.gateway.framework.error

import org.springframework.http.HttpStatus

/**
 * One error the gateway can return: the HTTP status, a stable machine-readable code and a message
 * for the caller.
 *
 * The framework defines the contract, applications define the constants — normally as an enum, so
 * the codes are enumerable and the compiler catches a typo:
 *
 * ```
 * enum class RouteErrorCode(
 *     override val status: HttpStatus,
 *     override val message: String,
 * ) : GatewayErrorCode {
 *     NOT_FOUND_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 없거나 인증정보가 올바르지 않습니다."),
 *     ;
 *     override val code get() = name
 * }
 * ```
 *
 * @see GatewayException
 * @see GatewayExceptionHandler
 */
interface GatewayErrorCode {

    /** Status of the HTTP response. */
    val status: HttpStatus

    /**
     * Machine-readable code the caller branches on. Stable across message rewording, so it is part
     * of the API contract — an enum's `name` is the usual source.
     */
    val code: String

    /** Message for the caller. Goes into the response body as is, so no internal detail. */
    val message: String
}
