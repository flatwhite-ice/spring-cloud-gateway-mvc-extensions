package com.example.gateway.routing.filter

import com.example.gateway.framework.filter.registry.BeanAliasFilterSupplier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Publishes this application's hand-written filter aliases.
 *
 * Lives on the application side of the boundary, which is what keeps the dependency one-way: the
 * framework consumes [BeanAliasFilterSupplier] beans through an `ObjectProvider` and never names an
 * alias class itself, so nothing under `framework/` has to know which filters an application
 * happens to declare.
 *
 * Filters that take no arguments need nothing here — annotating them
 * `@GatewayFilterBean` is enough.
 */
@Configuration(proxyBeanMethods = false)
class GatewayArgsFilterConfiguration {

    @Bean
    fun routeFilterAliasSupplier() = BeanAliasFilterSupplier(GatewayArgsFilterAliases::class.java)
}
