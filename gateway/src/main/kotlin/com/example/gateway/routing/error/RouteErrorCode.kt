package com.example.gateway.routing.error

import com.example.gateway.framework.error.GatewayErrorCode
import org.springframework.http.HttpStatus

/**
 * Errors this application's route filters return.
 *
 * An enum so the codes are enumerable and a typo is a compile error. [code] is the constant's name,
 * which makes the wire contract and the source read the same.
 */
enum class RouteErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : GatewayErrorCode {

    /** No `Authorization` header, or the token did not resolve to a user. */
    NOT_FOUND_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 없거나 인증정보가 올바르지 않습니다."),
    ;

    override val code get() = name
}
