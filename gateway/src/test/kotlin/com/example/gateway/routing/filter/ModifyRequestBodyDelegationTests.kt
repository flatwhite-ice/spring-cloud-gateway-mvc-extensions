package com.example.gateway.routing.filter

import com.example.gateway.support.DownstreamStubTests
import com.example.gateway.support.GatewayClient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

/**
 * `modifyRequestBody` has no YAML shorthand, so the spec left it open whether a filter bean could
 * expose it to a YAML route by holding it as a delegate. It can: this test is the evidence.
 *
 * @see com.example.gateway.routing.filter.JsonEnvelopeFilter
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("routes-alias")
class ModifyRequestBodyDelegationTests : DownstreamStubTests() {

    @LocalServerPort
    private var port = 0

    @Test
    fun `a filter bean can delegate to modifyRequestBody`() {
        downstream.stubFor(post(urlPathEqualTo("/echo/thing")).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).post(
            "/echo/thing",
            """{"a":1}""",
            HttpHeaders.AUTHORIZATION, "Bearer opaque-u1",
            HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE,
        )

        assertThat(response.statusCode()).isEqualTo(200)
        downstream.verify(
            postRequestedFor(urlPathEqualTo("/echo/thing"))
                .withRequestBody(equalToJson("""{"payload":{"a":1}}""")),
        )
    }

    @Test
    fun `a bodyless request reaches the rewrite function as null`() {
        downstream.stubFor(post(urlPathEqualTo("/echo/thing")).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).post(
            "/echo/thing",
            "",
            HttpHeaders.AUTHORIZATION, "Bearer opaque-u1",
            HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE,
        )

        assertThat(response.statusCode()).isEqualTo(200)
        downstream.verify(
            postRequestedFor(urlPathEqualTo("/echo/thing"))
                .withRequestBody(equalToJson("""{"payload":null}""")),
        )
    }
}
