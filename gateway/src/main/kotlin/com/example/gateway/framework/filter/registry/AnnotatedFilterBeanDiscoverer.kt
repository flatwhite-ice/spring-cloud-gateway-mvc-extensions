package com.example.gateway.framework.filter.registry

import com.example.gateway.framework.filter.GatewayFilterAlias
import com.example.gateway.framework.filter.GatewayFilterBean
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.cloud.gateway.server.mvc.filter.FilterBeanFactoryDiscoverer
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.DefaultOperationMethod

/**
 * Makes [GatewayFilterBean] beans addressable from YAML by their bean name.
 *
 * `AbstractGatewayDiscoverer` keys its operation map by `Method.getName()`, which is why the
 * hand-written-alias approach needs one method per filter *named after the filter*. Registering the
 * entries here instead decouples the two: every alias method can be called
 * [GatewayFilterAlias.METHOD_NAME] — so an interface can mandate it — while the YAML name comes
 * from the bean.
 *
 * **This is the project's only coupling to gateway internals.** `FilterAutoConfiguration` registers
 * `FilterBeanFactoryDiscoverer` without `@ConditionalOnMissingBean`, so replacing it means adding a
 * `@Primary` bean of the same type rather than overriding a definition; see
 * [com.example.gateway.framework.filter.config.GatewayFilterConfiguration]. `DiscovererReplacementTests` pins the arrangement down so a
 * gateway upgrade that changes it fails loudly instead of quietly dropping every filter.
 */
class AnnotatedFilterBeanDiscoverer(
    beanFactory: ListableBeanFactory,
    private val registry: GatewayFilterBeanRegistry,
) : FilterBeanFactoryDiscoverer(beanFactory) {

    override fun discover() {
        // Built-in filters and any hand-written alias classes, keyed by method name as usual.
        super.discover()

        val existing = operations.keys.toSet()
        val collisions = mutableListOf<String>()
        registry.aliasMethods().forEach { (beanName, method) ->
            if (beanName in existing) {
                collisions += "  - '$beanName' (${method.declaringClass.simpleName})"
                return@forEach
            }
            operations.add(beanName, DefaultOperationMethod(method))
            log.debug("Registered @GatewayFilterBean '$beanName' -> ${method.declaringClass.name}")
        }
        check(collisions.isEmpty()) {
            "@GatewayFilterBean bean names collide with filters the gateway already knows:\n" +
                collisions.joinToString("\n") +
                "\nA route's filter name is resolved by argument count, and these aliases take none, so " +
                "reusing a name makes the route's meaning depend on which entry was registered first. " +
                "Rejected outright rather than reasoned about. Rename the beans, e.g. " +
                "@GatewayFilterBean(\"myRetry\")."
        }
    }
}
