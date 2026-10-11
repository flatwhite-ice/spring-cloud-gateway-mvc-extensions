package com.example.gateway.routing.filter

import com.example.gateway.framework.filter.trace.FilterTrace
import com.example.gateway.support.DownstreamStubTests
import com.example.gateway.support.GatewayClient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
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
 * Pins down how the gateway turns a YAML filter name into an alias function name.
 *
 * `RouterFunctionHolderFactory.translate()` applies `StringUtils.uncapitalize(name)` and nothing
 * else, which is what lets a lower-camel-case alias be referenced directly. If an upgrade
 * introduced extra normalisation (kebab-case, for instance) this test would catch it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("routes-case")
class AliasNameMatchingTests : DownstreamStubTests() {

    @LocalServerPort
    private var port = 0

    @Test
    fun `lower camel case filter name resolves`() {
        assertRouteAppliesBothFilters("/uncapitalized/1")
    }

    @Test
    fun `capitalized filter name resolves to the same alias`() {
        assertRouteAppliesBothFilters("/capitalized/1")
    }

    private fun assertRouteAppliesBothFilters(path: String) {
        downstream.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).get(path, HttpHeaders.AUTHORIZATION, "Bearer opaque-u1")

        assertThat(response.statusCode()).isEqualTo(200)
        downstream.verify(
            getRequestedFor(urlPathEqualTo(path))
                .withHeader(FilterTrace.HEADER, equalTo("authFilter,tenantFilter")),
        )
    }
}
