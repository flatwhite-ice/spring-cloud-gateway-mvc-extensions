package com.example.gateway.routing.filter

import com.example.errorhandling.GlobalFilterExceptionHandler
import com.example.errorhandling.PaymentRequiredFilter
import com.example.errorhandling.ScopedAdviceFilter
import com.example.errorhandling.ScopedFilterExceptionHandler
import com.example.errorhandling.ThrowingAuthFilter
import com.example.gateway.support.DownstreamStubTests
import com.example.gateway.support.GatewayClient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles

/**
 * A route filter can signal failure by throwing instead of returning a short-circuit
 * `ServerResponse`, and the exception goes through Spring MVC's ordinary error handling.
 *
 * That works because a route's filters run inside `HandlerFunctionAdapter`, i.e. inside
 * `DispatcherServlet.doDispatch`, so a thrown exception reaches `processHandlerException` and the
 * usual `HandlerExceptionResolver` chain.
 *
 * `AuthFilter` throws too, but a `GatewayException`, which `GatewayExceptionHandler` renders into the
 * project's standard error body. `UserRequirementFilterFactory` is the one that builds a
 * short-circuit `ServerResponse.status(UNAUTHORIZED)` itself. Both work; throwing is worth it when
 * several routes should share one error body, which is what the advice below does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("routes-errors")
class GatewayFilterErrorHandlingTests : DownstreamStubTests() {

    @LocalServerPort
    private var port = 0

    @Test
    fun `a custom exception is handled by an unscoped ControllerAdvice`() {
        downstream.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).get("/custom-exception/1")

        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(response.body()).contains("\"error\":\"unauthorized\"", "\"detail\":\"no token\"")
        // throwing short-circuits just as returning a response does
        downstream.verify(exactly(0), anyRequestedFor(anyUrl()))
    }

    @Test
    fun `a ResponseStatusException needs no advice at all`() {
        downstream.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).get("/response-status-exception/1")

        // ResponseStatusExceptionResolver honours the status, then Spring forwards to /error where
        // GatewayErrorAttributes renders the same body shape as a GatewayException would.
        assertThat(response.statusCode()).isEqualTo(402)
        assertThat(response.body()).contains("\"error_code\":\"PAYMENT_REQUIRED\"")
        downstream.verify(exactly(0), anyRequestedFor(anyUrl()))
    }

    /**
     * The gotcha worth knowing: `@ControllerAdvice(basePackages = ...)` — or `assignableTypes`, or
     * `annotations` — never matches a gateway route. `ExceptionHandlerExceptionResolver` tests the
     * selector against the handler's type, and a functional route's handler is a `HandlerFunction`,
     * not a `HandlerMethod`, so there is no type to test and the advice is skipped. Only an
     * unscoped `@ControllerAdvice` applies.
     */
    @Test
    fun `a selector-scoped ControllerAdvice does not apply to gateway routes`() {
        downstream.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)))

        val response = GatewayClient(port).get("/scoped-advice/1")

        assertThat(response.statusCode())
            .describedAs("the scoped advice would have returned 418 if it had matched")
            .isEqualTo(500)
        assertThat(response.body()).doesNotContain("scoped-advice-ran")
        // falls through to GatewayErrorAttributes, which keeps the body shape and says nothing
        // about the exception
        assertThat(response.body())
            .contains("\"error_code\":\"INTERNAL_SERVER_ERROR\"")
            .doesNotContain("ScopedException", "scoped")
    }

    /**
     * A request that matches no route never throws — Spring calls `sendError` — so no
     * `@ControllerAdvice` can reach it. [com.example.gateway.framework.error.GatewayErrorAttributes]
     * is what keeps that 404 in the same shape as every other error.
     */
    @Test
    fun `a request matching no route gets the same body shape`() {
        val response = GatewayClient(port).get("/nothing-routes-here")

        assertThat(response.statusCode()).isEqualTo(404)
        assertThat(response.body())
            .contains("\"error_code\":\"NOT_FOUND\"")
            .contains("\"error_message\"")
            // Boot's default body would have carried these
            .doesNotContain("timestamp", "\"path\"")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ErrorHandlingFilterConfiguration {

        // Bean names are the YAML filter names; the fixtures live outside the scanned packages so
        // they cannot affect any other test's context.
        @Bean fun throwingAuthFilter() = ThrowingAuthFilter()

        @Bean fun paymentRequiredFilter() = PaymentRequiredFilter()

        @Bean fun scopedAdviceFilter() = ScopedAdviceFilter()

        @Bean fun globalFilterExceptionHandler() = GlobalFilterExceptionHandler()

        @Bean fun scopedFilterExceptionHandler() = ScopedFilterExceptionHandler()
    }
}
