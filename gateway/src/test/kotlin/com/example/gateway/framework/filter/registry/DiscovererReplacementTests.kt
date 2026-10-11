package com.example.gateway.framework.filter.registry

import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.server.mvc.filter.FilterBeanFactoryDiscoverer
import org.springframework.context.ApplicationContext

/**
 * Guards the project's only coupling to gateway internals.
 *
 * [AnnotatedFilterBeanDiscoverer] only takes effect because it wins the by-type injection into the
 * gateway's `RouterFunctionHolderFactory`, which it does through `@Primary` rather than through
 * definition overriding — `FilterAutoConfiguration` declares `filterBeanFactoryDiscoverer` without
 * `@ConditionalOnMissingBean`. If a gateway upgrade changed that arrangement, every
 * `@GatewayFilterBean` filter would silently stop being registered. These tests turn that into a
 * build failure.
 */
@SpringBootTest
class DiscovererReplacementTests {

    @Autowired
    private lateinit var context: ApplicationContext

    private val discoverer get() = context.getBean(FilterBeanFactoryDiscoverer::class.java)

    @Test
    fun `resolving FilterBeanFactoryDiscoverer by type yields ours`() {
        // This is exactly how RouterFunctionHolderFactory obtains it.
        assertThat(discoverer).isInstanceOf(AnnotatedFilterBeanDiscoverer::class.java)
    }

    @Test
    fun `annotated filter beans are registered under their bean names`() {
        assertThat(discoverer.operations.keys)
            .contains("authFilter", "tenantFilter", "jsonEnvelopeFilter")
    }

    @Test
    fun `hand-written aliases and built-in filters survive alongside them`() {
        assertThat(discoverer.operations.keys)
            .contains("requireUser")
            .contains("addRequestHeader", "stripPrefix", "setPath")
    }
}
