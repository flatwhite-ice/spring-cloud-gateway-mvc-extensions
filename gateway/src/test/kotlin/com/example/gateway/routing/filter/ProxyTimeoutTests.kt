package com.example.gateway.routing.filter

import com.example.gateway.support.DownstreamStubTests
import com.example.gateway.support.GatewayClient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles

/**
 * Pins down that `spring.http.clients.read-timeout` actually reaches the client the gateway
 * proxies with, so a slow downstream fails fast instead of holding the request open.
 *
 * This matters because `spring.threads.virtual.enabled` is on: the servlet thread pool no longer
 * caps concurrency, so a hanging downstream would accumulate requests rather than being rejected
 * by a full pool. The timeout is what bounds it.
 *
 * The timeout is overridden here rather than relying on the 5s default, so the test stays quick.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.http.clients.read-timeout=300ms"],
)
@ActiveProfiles("routes-alias")
class ProxyTimeoutTests : DownstreamStubTests() {

    @LocalServerPort
    private var port = 0

    private val gateway by lazy { GatewayClient(port) }

    @Test
    fun `a downstream that stalls before responding trips the read timeout`() {
        downstream.stubFor(
            get(urlPathEqualTo("/orders/1"))
                .willReturn(aResponse().withStatus(200).withBody("too late").withFixedDelay(3_000)),
        )

        val elapsed = timed {
            val response = gateway.get("/orders/1", HttpHeaders.AUTHORIZATION, "Bearer opaque-u1")

            // The timeout surfaces as an exception from the proxy exchange, which no advice
            // handles, so it falls through to GatewayErrorAttributes — same body shape as any
            // other gateway error, with nothing about the cause in it.
            assertThat(response.statusCode()).isEqualTo(500)
            assertThat(response.body())
                .contains("\"error_code\":\"INTERNAL_SERVER_ERROR\"")
                .doesNotContain("too late", "HttpTimeout", "timeout")
        }

        // Fails fast on the 300ms timeout rather than waiting out the 3s downstream delay.
        assertThat(elapsed).isLessThan(Duration.ofMillis(2_500))
    }

    /**
     * The semantics, which are easy to get wrong. Boot maps `read-timeout` to
     * `HttpRequest.Builder#timeout`, and that bounds the **whole exchange**, not just the wait
     * for the response to start. A body that begins arriving immediately but dribbles out past
     * the timeout is still cut off, and the client sees the gateway's 500 rather than a short
     * body.
     *
     * So `read-timeout` is effectively a total request deadline. A route that serves large or
     * long-lived responses needs its own, longer value — a short global timeout will truncate it.
     */
    @Test
    fun `a body that dribbles out past the timeout is cut off too`() {
        downstream.stubFor(
            get(urlPathEqualTo("/orders/2")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("dribbled")
                    // Response starts at once; the 8 bytes then trickle out over ~1.2s, well
                    // past the 300ms timeout.
                    .withChunkedDribbleDelay(8, 1_200),
            ),
        )

        val response = gateway.get("/orders/2", HttpHeaders.AUTHORIZATION, "Bearer opaque-u1")

        assertThat(response.statusCode()).isEqualTo(500)
        assertThat(response.body()).doesNotContain("dribbled")
    }

    /** The other side of the same rule: a body that finishes inside the timeout arrives intact. */
    @Test
    fun `a body that dribbles out within the timeout arrives intact`() {
        downstream.stubFor(
            get(urlPathEqualTo("/orders/3")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("dribbled")
                    .withChunkedDribbleDelay(8, 120),
            ),
        )

        val response = gateway.get("/orders/3", HttpHeaders.AUTHORIZATION, "Bearer opaque-u1")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).isEqualTo("dribbled")
    }

    private fun timed(block: () -> Unit): Duration {
        val start = System.nanoTime()
        block()
        return Duration.ofNanos(System.nanoTime() - start)
    }
}
