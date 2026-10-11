package com.example.gateway.framework.filter.config

import com.example.gateway.framework.filter.registry.AnnotatedFilterBeanDiscoverer
import com.example.gateway.framework.filter.registry.BeanAliasFilterSupplier
import com.example.gateway.framework.filter.registry.GatewayFilterBeanRegistry
import com.example.gateway.framework.filter.validation.GatewayFilterValidator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Wires the annotation-driven filter registration and the startup checks.
 *
 * Registers nothing application specific. Hand-written alias classes are published by the
 * application's own [BeanAliasFilterSupplier] beans, which this configuration only *consumes*
 * through [ObjectProvider] — so the framework never needs to know which aliases exist, or that any
 * exist at all.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayFilterProperties::class)
class GatewayFilterConfiguration {

    @Bean
    fun gatewayFilterBeanRegistry(applicationContext: ApplicationContext) =
        GatewayFilterBeanRegistry(applicationContext)

    /**
     * Takes over from the gateway's own `filterBeanFactoryDiscoverer`.
     *
     * `FilterAutoConfiguration` declares that bean without `@ConditionalOnMissingBean`, so it cannot
     * be replaced by definition overriding. `@Primary` on a second bean of the same type is the
     * supported way to win the by-type injection into `RouterFunctionHolderFactory`. The gateway's
     * own instance is still created but never consulted.
     */
    @Bean
    @Primary
    fun annotatedFilterBeanDiscoverer(
        applicationContext: ApplicationContext,
        registry: GatewayFilterBeanRegistry,
    ) = AnnotatedFilterBeanDiscoverer(applicationContext, registry)

    @Bean
    fun gatewayFilterValidator(
        applicationContext: ApplicationContext,
        properties: GatewayFilterProperties,
        registry: GatewayFilterBeanRegistry,
        aliasSuppliers: ObjectProvider<BeanAliasFilterSupplier>,
        allFilterSuppliers: ObjectProvider<FilterSupplier>,
    ) = GatewayFilterValidator(
        applicationContext,
        properties,
        registry,
        aliasSuppliers,
        allFilterSuppliers,
    )
}
