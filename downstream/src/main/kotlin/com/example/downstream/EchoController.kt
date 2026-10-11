package com.example.downstream

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Catch-all echo endpoint. Covers both routes in the gateway's `application.yml` (the `/orders`
 * and `/echo` prefixes) and anything else you point a route at, so no mapping has to be kept in
 * sync.
 *
 * The headers the gateway's filters inject are pulled out into their own section of the response
 * and logged separately, because those are what you are actually verifying.
 */
@RestController
class EchoController {

    @RequestMapping("/**")
    fun echo(
        request: HttpServletRequest,
        @RequestBody(required = false) body: String?,
    ): EchoResponse {
        val headers = request.headerNames.asSequence()
            .associateWithTo(sortedMapOf(String.CASE_INSENSITIVE_ORDER)) { request.getHeader(it) }
        val gatewayHeaders = headers.filterKeys { it in GATEWAY_INJECTED_HEADERS }

        val query = request.queryString?.let { "?$it" } ?: ""
        logger.info("<< {} {}{}", request.method, request.requestURI, query)
        if (gatewayHeaders.isEmpty()) {
            logger.warn("   no gateway headers: was this request sent straight here instead of through :8080?")
        } else {
            gatewayHeaders.forEach { (name, value) -> logger.info("   {}: {}", name, value) }
        }
        if (!body.isNullOrEmpty()) {
            logger.info("   body: {}", body)
        }

        return EchoResponse(
            service = "downstream-dummy",
            method = request.method,
            path = request.requestURI,
            query = request.queryString,
            gatewayHeaders = gatewayHeaders,
            headers = headers,
            body = body,
        )
    }

    private companion object {

        private val logger = LoggerFactory.getLogger(EchoController::class.java)

        /**
         * Set by AuthFilter, TenantFilter, FilterTrace and the route's AddRequestHeader shorthand
         * in the gateway project. Listed by name rather than by import so this module stays
         * independent of the gateway module.
         */
        private val GATEWAY_INJECTED_HEADERS = sortedSetOf(
            String.CASE_INSENSITIVE_ORDER,
            "X-User-Id",
            "X-Tenant-Id",
            "X-Filter-Trace",
            "X-Gateway",
        )
    }
}

data class EchoResponse(
    val service: String,
    val method: String,
    val path: String,
    val query: String?,
    val gatewayHeaders: Map<String, String>,
    val headers: Map<String, String>,
    val body: String?,
)
