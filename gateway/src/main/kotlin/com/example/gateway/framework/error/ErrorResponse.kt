package com.example.gateway.framework.error

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Error body every gateway error response carries.
 *
 * Field names are snake_case on the wire and camelCase in Kotlin, spelled out with [JsonProperty]
 * rather than left to a global naming strategy — a strategy would also rewrite every other payload
 * the gateway happens to serialize.
 *
 * The HTTP status is not repeated here: it is already the response's status line.
 */
data class ErrorResponse(

    @get:JsonProperty("error_code")
    val errorCode: String,

    @get:JsonProperty("error_message")
    val errorMessage: String,
) {

    constructor(errorCode: GatewayErrorCode) : this(errorCode.code, errorCode.message)
}
