package com.example.gateway.routing.filter

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
import org.springframework.test.context.ActiveProfiles

/**
 * Registering our own `FilterSupplier` bean puts its operations into the same map as the built-in
 * ones — the gateway loads every `FilterSupplier` bean through a single `getBeanProvider` call. This
 * checks the built-ins still resolve, in both the shorthand and the long form.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("routes-builtin")
class BuiltInFilterRegressionTests : DownstreamStubTests() {

    @LocalServerPort
    private var port = 0

    @Test
    fun `built in filters still apply`() {
        downstream.stubFor(get(urlPathEqualTo("/thing")).willReturn(aResponse().withStatus(200).withBody("built-in")))

        val response = GatewayClient(port).get("/builtin/thing")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).isEqualTo("built-in")
        // StripPrefix=1 removed /builtin, and both AddRequestHeader forms applied
        downstream.verify(
            getRequestedFor(urlPathEqualTo("/thing"))
                .withHeader("X-Built-In", equalTo("yes"))
                .withHeader("X-Built-In-Long-Form", equalTo("yes")),
        )
    }
}
