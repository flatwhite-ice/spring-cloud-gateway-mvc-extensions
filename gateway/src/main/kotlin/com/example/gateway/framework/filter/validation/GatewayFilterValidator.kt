package com.example.gateway.framework.filter.validation

import com.example.gateway.framework.filter.GatewayFilterAlias
import com.example.gateway.framework.filter.GatewayFilterBean
import com.example.gateway.framework.filter.config.GatewayFilterProperties
import com.example.gateway.framework.filter.delegate.BeanDelegatingFilter
import com.example.gateway.framework.filter.delegate.BeanTarget
import com.example.gateway.framework.filter.registry.BeanAliasFilterSupplier
import com.example.gateway.framework.filter.registry.GatewayFilterBeanRegistry
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.cloud.gateway.server.mvc.filter.FilterDiscoverer
import org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier
import org.springframework.context.ApplicationContext
import org.springframework.web.servlet.function.HandlerFilterFunction

/**
 * Fails startup on the mistakes this indirection makes easy, with messages that name the file to
 * fix.
 *
 * Runs as a [SmartInitializingSingleton], so it executes inside
 * `finishBeanFactoryInitialization()` — before `finishRefresh()` starts the web server. A broken
 * configuration therefore never serves a request.
 *
 * It re-runs [GatewayFilterBeanRegistry.aliasMethods] even though
 * [com.example.gateway.framework.filter.registry.AnnotatedFilterBeanDiscoverer] already called it:
 * the discoverer only runs while routes are being built, and a project with no configured routes
 * would otherwise never check its filters at all. The call is idempotent.
 *
 * A YAML route naming a filter that does not exist needs no code here: `routerFunctionMapping`
 * collects the `RouterFunction` beans during refresh, which forces the gateway to build every
 * configured route, so an unknown filter name already fails the context. `StartupFailureTests` pins
 * that behaviour down so a gateway upgrade that made route building lazy would be caught.
 */
class GatewayFilterValidator(
    private val applicationContext: ApplicationContext,
    private val properties: GatewayFilterProperties,
    private val registry: GatewayFilterBeanRegistry,
    private val aliasSuppliers: ObjectProvider<BeanAliasFilterSupplier>,
    private val allFilterSuppliers: ObjectProvider<FilterSupplier>,
) : SmartInitializingSingleton {

    override fun afterSingletonsInstantiated() {
        val annotated = registry.aliasMethods()
        val manual = collectManualAliases()
        logger.debug("Route filters from @GatewayFilterBean: {}", annotated.keys)
        if (manual.names.isNotEmpty()) {
            logger.debug("Route filters from hand-written aliases: {}", manual.names)
        }

        if (properties.failOnNameCollision) {
            checkForNameCollisions(annotated.keys + manual.names)
        }
        if (properties.validateBeans) {
            checkManualAliasTargets(manual.targets)
        }
    }

    /**
     * Hand-written aliases in [BeanAliasFilterSupplier] classes. Still supported for filters that
     * take per-route arguments, which [GatewayFilterBean] cannot express.
     *
     * Aliases that take parameters cannot be called here, so only their names are collected; their
     * delegate is checked on the first request that uses them.
     */
    private fun collectManualAliases(): ManualAliases {
        val names = LinkedHashSet<String>()
        val targets = LinkedHashMap<String, BeanTarget>()
        val errors = mutableListOf<String>()

        aliasSuppliers.orderedStream().forEach { supplier ->
            for (method in supplier.get()) {
                if (!returnsFilter(method)) continue
                if (!Modifier.isStatic(method.modifiers)) {
                    // Only reachable from Kotlin: without @JvmStatic the function compiles to an
                    // instance method, which the gateway registers and then cannot invoke.
                    errors += "  - ${method.declaringClass.name}.${method.name}() is not static; declare " +
                        "hand-written aliases in an `object` and annotate them @JvmStatic"
                    continue
                }
                names += method.name
                if (method.parameterCount > 0) continue
                val filter = runCatching { method.invoke(null) }.getOrElse { ex ->
                    val cause = ex.cause ?: ex
                    errors += "  - ${method.declaringClass.name}.${method.name}() threw " +
                        "${cause.javaClass.simpleName}: ${cause.message}"
                    continue
                }
                (filter as? BeanDelegatingFilter)?.let { targets[method.name] = it.target }
            }
        }

        check(errors.isEmpty()) {
            "Hand-written gateway filter aliases must be static and callable without side effects:\n" +
                errors.joinToString("\n")
        }
        return ManualAliases(names, targets)
    }

    private fun checkForNameCollisions(filterNames: Set<String>) {
        val collisions = (filterNames intersect builtInFilterNames()).toSortedSet()
        check(collisions.isEmpty()) {
            "Gateway filter names collide with built-in filters: $collisions. A route's filter name is " +
                "resolved by argument count, so for a same-arity collision which filter a route gets " +
                "depends on the order the suppliers happened to be registered in. Rename them."
        }
    }

    /**
     * Every filter name the gateway already knows about: the built-in `FilterSupplier` beans, plus
     * any supplier still registered through `META-INF/spring.factories`. Collected from the live
     * suppliers rather than a hand-maintained deny list, so it stays correct across gateway
     * upgrades.
     */
    private fun builtInFilterNames(): Set<String> {
        val names = LinkedHashSet<String>()
        allFilterSuppliers.orderedStream()
            .filter { it !is BeanAliasFilterSupplier }
            .forEach { supplier -> supplier.get().filter(::returnsFilter).forEach { names += it.name } }
        names += FilterDiscoverer().operations.keys
        return names
    }

    private fun checkManualAliasTargets(targets: Map<String, BeanTarget>) {
        val errors = targets.mapNotNull { (alias, target) -> describeMissingBean(alias, target) }
        check(errors.isEmpty()) {
            "Hand-written gateway filter aliases reference beans that do not exist or have the wrong " +
                "type:\n" + errors.joinToString("\n")
        }
    }

    private fun describeMissingBean(alias: String, target: BeanTarget): String? = when (target) {
        is BeanTarget.ByName -> when {
            !applicationContext.containsBean(target.beanName) ->
                "  - alias $alias() -> no bean named '${target.beanName}'"
            !applicationContext.isTypeMatch(target.beanName, HandlerFilterFunction::class.java) ->
                "  - alias $alias() -> bean '${target.beanName}' is a " +
                    "${applicationContext.getType(target.beanName)}, not a HandlerFilterFunction"
            else -> null
        }
        is BeanTarget.ByType -> missingByType(alias, target.type)
        is BeanTarget.ByFactory<*> -> missingByType(alias, target.type)
    }

    private fun missingByType(alias: String, type: Class<*>): String? {
        val candidates = applicationContext.getBeanNamesForType(type)
        return when (candidates.size) {
            1 -> null
            0 -> "  - alias $alias() -> no bean of type ${type.name}"
            else -> "  - alias $alias() -> ${candidates.size} beans of type ${type.simpleName} " +
                "(${candidates.joinToString()}); resolution by type is ambiguous"
        }
    }

    private class ManualAliases(val names: Set<String>, val targets: Map<String, BeanTarget>)

    private companion object {

        private val logger = LoggerFactory.getLogger(GatewayFilterValidator::class.java)

        private fun returnsFilter(method: Method) =
            HandlerFilterFunction::class.java.isAssignableFrom(method.returnType)
    }
}
