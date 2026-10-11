package com.example.gateway.routing.filter

import com.example.gateway.framework.filter.GatewayFilterAlias
import com.example.gateway.framework.filter.GatewayFilterBean
import com.example.gateway.framework.filter.beanFilter
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

/**
 * Wraps the request body in `{"payload": ...}`.
 *
 * Shows how to use a built-in filter that has no YAML shorthand: the bean holds the
 * `modifyRequestBody` request processor and delegates to it, which makes it usable from a YAML
 * route like any other named filter.
 *
 * The body is read as `String` rather than a `Map` or data class so the bytes are not
 * reserialized — field order and number formatting stay intact, which matters when something
 * downstream verifies a signature. Note that `modifyRequestBody` buffers the whole body in
 * memory, so do not put this on upload or streaming routes. A body-less request is passed to the
 * rewrite function as `null`.
 */
@GatewayFilterBean
class JsonEnvelopeFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    private val delegate: HandlerFilterFunction<ServerResponse, ServerResponse> =
        HandlerFilterFunction.ofRequestProcessor(
            BeforeFilterFunctions.modifyRequestBody(
                String::class.java,
                String::class.java,
                MediaType.APPLICATION_JSON_VALUE,
            ) { _, body -> wrapInEnvelope(body) },
        )

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): ServerResponse =
        delegate.filter(request, next)

    companion object : GatewayFilterAlias {

        private val logger = LoggerFactory.getLogger(JsonEnvelopeFilter::class.java)

        @JvmStatic
        override fun alias() = beanFilter<JsonEnvelopeFilter>()

        private fun wrapInEnvelope(body: String?): String {
            val envelope = """{"payload":${if (body.isNullOrBlank()) "null" else body}}"""
            logger.info("jsonEnvelopeFilter: wrapped body {} -> {} bytes", body?.length ?: 0, envelope.length)
            return envelope
        }
    }
}
