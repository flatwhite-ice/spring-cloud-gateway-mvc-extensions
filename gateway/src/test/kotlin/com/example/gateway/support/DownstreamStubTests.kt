package com.example.gateway.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Base class for tests that proxy to a stubbed downstream.
 *
 * One stub serves the whole test JVM: the property is on this base class, so subclasses share it,
 * and stopping it per class would pull the port out from under the next class (and from under
 * cached application contexts that already resolved `downstream.url`). Each test resets the stubs
 * instead, and the JVM shutdown hook does the stopping.
 *
 * The stub starts from the companion initializer rather than `@BeforeAll` so its port is known
 * before Spring resolves `downstream.url`, whatever order the JUnit extensions happen to run in.
 */
abstract class DownstreamStubTests {

    @BeforeEach
    fun resetDownstream() {
        downstream.resetAll()
    }

    companion object {

        @JvmStatic
        val downstream: WireMockServer = WireMockServer(
            // http2PlainDisabled: the gateway proxies with the JDK HttpClient, which tries an h2c
            // upgrade on plaintext. WireMock accepts the upgrade and then resets the stream, which
            // surfaces as a 500 on every request that carries a body. A real HTTP/1.1 downstream
            // simply declines the upgrade, so this is a quirk of the stub, not of the gateway.
            WireMockConfiguration.options().dynamicPort().http2PlainDisabled(true),
        )

        init {
            downstream.start()
            Runtime.getRuntime().addShutdownHook(Thread(downstream::stop, "downstream-stub-shutdown"))
        }

        @JvmStatic
        @DynamicPropertySource
        fun downstreamUrl(registry: DynamicPropertyRegistry) {
            registry.add("downstream.url", downstream::baseUrl)
        }
    }
}
