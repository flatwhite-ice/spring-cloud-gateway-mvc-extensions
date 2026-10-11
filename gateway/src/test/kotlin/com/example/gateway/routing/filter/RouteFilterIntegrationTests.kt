package com.example.gateway.routing.filter

import com.example.gateway.framework.filter.trace.FilterTrace
import com.example.gateway.routing.error.RouteErrorCode
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
 * End to end checks for filter beans referenced by name from a YAML route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("routes-alias")
class RouteFilterIntegrationTests : DownstreamStubTests() {

    @LocalServerPort
    private var port = 0

    private val gateway by lazy { GatewayClient(port) }

    @Test
    fun `filter beans inject headers into the downstream request`() {
        downstream.stubFor(get(urlPathEqualTo("/orders/1")).willReturn(aResponse().withStatus(200).withBody("ok")))

        val response = gateway.get("/orders/1", HttpHeaders.AUTHORIZATION, "Bearer opaque-u1")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).isEqualTo("ok")
        downstream.verify(
            getRequestedFor(urlPathEqualTo("/orders/1"))
                .withHeader(AuthFilter.USER_ID_HEADER, equalTo("u1"))
                .withHeader(TenantFilter.TENANT_ID_HEADER, equalTo("tenant-u1"))
                // the built-in shorthand filter on the same route still applies
                .withHeader("X-Gateway", equalTo("mvc-extensions")),
        )
    }

    @Test
    fun `filters run in the order the yaml lists them`() {
        downstream.stubFor(get(urlPathEqualTo("/orders/1")).willReturn(aResponse().withStatus(200)))

        gateway.get("/orders/1", HttpHeaders.AUTHORIZATION, "Bearer opaque-u1")

        // tenantFilter derives the tenant from the header authFilter set, so this value also
        // proves authFilter ran first.
        downstream.verify(
            getRequestedFor(urlPathEqualTo("/orders/1"))
                .withHeader(FilterTrace.HEADER, equalTo("authFilter,tenantFilter")),
        )
    }

    @Test
    fun `unauthenticated requests short circuit without calling the downstream`() {
        downstream.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)))

        val response = gateway.get("/orders/1")

        // AuthFilter throws GatewayException(NOT_FOUND_TOKEN); GatewayExceptionHandler renders it.
        assertThat(response.statusCode()).isEqualTo(RouteErrorCode.NOT_FOUND_TOKEN.status.value())
        assertThat(response.body())
            .contains("\"error_code\":\"NOT_FOUND_TOKEN\"")
            .contains(RouteErrorCode.NOT_FOUND_TOKEN.message)
        downstream.verify(exactly(0), anyRequestedFor(anyUrl()))
    }

    @Test
    fun `client supplied user id header is replaced not appended`() {
        downstream.stubFor(get(urlPathEqualTo("/orders/1")).willReturn(aResponse().withStatus(200)))

        gateway.get(
            "/orders/1",
            HttpHeaders.AUTHORIZATION, "Bearer opaque-u1",
            AuthFilter.USER_ID_HEADER, "spoofed",
        )

        // ServerRequest.Builder.header() appends, so a spoofed value would arrive as "spoofed,u1".
        // AuthFilter uses headers { it.set(..) } to replace it outright.
        downstream.verify(
            getRequestedFor(urlPathEqualTo("/orders/1"))
                .withHeader(AuthFilter.USER_ID_HEADER, equalTo("u1")),
        )
    }
}
