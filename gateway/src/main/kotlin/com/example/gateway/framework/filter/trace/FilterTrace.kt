package com.example.gateway.framework.filter.trace

import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.function.ServerRequest

/**
 * Appends the name of each filter that ran to a request header, so integration tests can assert
 * that filters execute in the order the YAML lists them.
 */
object FilterTrace {

    /** Header carrying the comma separated list of filters that have run. */
    const val HEADER = "X-Filter-Trace"

    fun append(headers: HttpHeaders, request: ServerRequest, filterName: String) {
        val current = request.headers().firstHeader(HEADER)
        headers[HEADER] = if (current.isNullOrBlank()) filterName else "$current,$filterName"
    }
}