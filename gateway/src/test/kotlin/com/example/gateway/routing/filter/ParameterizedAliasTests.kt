package com.example.gateway.routing.filter

import com.example.gateway.support.DownstreamStubTests
import com.example.gateway.support.GatewayClient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles

/**
 * The one thing `@GatewayFilterBean` cannot express: the same filter configured differently per
 * route. Covered by a hand-written alias that takes arguments and hands them to a factory bean.
 *
 * Also proves the argument binding itself works, which depends on `javaParameters = true` in the
 * build — the gateway matches YAML `args` keys against parameter *names*.
 *
 * @see GatewayArgsFilterAliases.requireUser
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("routes-args")
class ParameterizedAliasTests : DownstreamStubTests() {

    @LocalServerPort
    private var port = 0

    @Test
    fun `strict mode rejects an unauthenticated request`() {
        downstream.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).get("/strict/1")

        assertThat(response.statusCode()).isEqualTo(401)
        downstream.verify(exactly(0), anyRequestedFor(anyUrl()))
    }

    @Test
    fun `lenient mode forwards an unauthenticated request as anonymous`() {
        downstream.stubFor(get(urlPathEqualTo("/lenient/1")).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).get("/lenient/1")

        assertThat(response.statusCode()).isEqualTo(200)
        downstream.verify(
            getRequestedFor(urlPathEqualTo("/lenient/1"))
                .withHeader(AuthFilter.USER_ID_HEADER, equalTo("anonymous")),
        )
    }

    @Test
    fun `an authenticated request passes in either mode`() {
        downstream.stubFor(get(urlPathEqualTo("/strict/1")).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).get("/strict/1", HttpHeaders.AUTHORIZATION, "Bearer opaque-u1")

        assertThat(response.statusCode()).isEqualTo(200)
        downstream.verify(
            getRequestedFor(urlPathEqualTo("/strict/1"))
                .withHeader(AuthFilter.USER_ID_HEADER, equalTo("u1")),
        )
    }
}
