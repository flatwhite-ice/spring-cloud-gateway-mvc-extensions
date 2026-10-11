package com.example.gateway.support

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Minimal HTTP client for driving the gateway under test. Uses the JDK client so a 401 is a status
 * code to assert on rather than an exception to unwrap.
 */
class GatewayClient(port: Int) {

    private val http: HttpClient = HttpClient.newHttpClient()

    private val baseUrl = "http://localhost:$port"

    fun get(path: String, vararg headers: String): HttpResponse<String> =
        send(request(path, *headers).GET())

    fun post(path: String, body: String, vararg headers: String): HttpResponse<String> =
        send(request(path, *headers).POST(HttpRequest.BodyPublishers.ofString(body)))

    private fun request(path: String, vararg headers: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
        headers.toList().chunked(2).forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun send(builder: HttpRequest.Builder): HttpResponse<String> =
        http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}
