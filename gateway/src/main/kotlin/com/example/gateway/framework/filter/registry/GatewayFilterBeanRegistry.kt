package com.example.gateway.framework.filter.registry

import com.example.gateway.framework.filter.GatewayFilterAlias
import com.example.gateway.framework.filter.GatewayFilterBean
import com.example.gateway.framework.filter.delegate.BeanDelegatingFilter
import com.example.gateway.framework.filter.delegate.BeanTarget
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.util.ReflectionUtils
import org.springframework.web.servlet.function.HandlerFilterFunction

/**
 * Finds the [GatewayFilterBean] beans and the static alias method each one exposes.
 *
 * Deliberately *not* a component scanner. [GatewayFilterBean] is meta-annotated with
 * `@Component`, so the ordinary component scan already registers these beans and
 * `AnnotationBeanNameGenerator` already derives their names; all that is left is to ask the bean
 * factory which beans carry the annotation. A scanner of our own would only risk disagreeing with
 * `@ComponentScan`, profiles and conditional registration.
 *
 * Shared by [AnnotatedFilterBeanDiscoverer], which runs while routes are being built, and
 * [com.example.gateway.framework.filter.validation.GatewayFilterValidator], which runs even when no routes are configured and the discoverer
 * is therefore never asked. Both call [aliasMethods], which is read-only and idempotent.
 */
class GatewayFilterBeanRegistry(private val beanFactory: ListableBeanFactory) {

    /**
     * Maps bean name (the YAML filter name) to the static method the gateway will invoke to build
     * the filter.
     *
     * Every problem is collected and reported in one exception, so a misconfigured project does not
     * have to be fixed one restart at a time.
     */
    fun aliasMethods(): Map<String, Method> {
        val aliasMethods = LinkedHashMap<String, Method>()
        val errors = mutableListOf<String>()

        for (beanName in beanFactory.getBeanNamesForAnnotation(GatewayFilterBean::class.java)) {
            val type = beanFactory.getType(beanName)
            if (type == null) {
                errors += "  - '$beanName': unable to determine its type"
                continue
            }
            if (!HandlerFilterFunction::class.java.isAssignableFrom(type)) {
                errors += "  - '$beanName' (${type.name}): annotated @GatewayFilterBean but does not " +
                    "implement HandlerFilterFunction<ServerResponse, ServerResponse>"
                continue
            }
            val method = findAliasMethod(type)
            if (method == null) {
                errors += "  - '$beanName' (${type.simpleName}): no static ${GatewayFilterAlias.METHOD_NAME}() " +
                    "method. Add `companion object : GatewayFilterAlias { @JvmStatic override fun " +
                    "${GatewayFilterAlias.METHOD_NAME}() = beanFilter<${type.simpleName}>() }` — and if the " +
                    "companion object is already there, @JvmStatic is what is missing (without it the " +
                    "override compiles to an instance method and no static method reaches the gateway)"
                continue
            }
            val mismatch = targetMismatch(beanName, type, method)
            if (mismatch != null) {
                errors += "  - '$beanName' (${type.simpleName}): $mismatch"
                continue
            }
            aliasMethods[beanName] = method
        }

        check(errors.isEmpty()) {
            "@GatewayFilterBean beans are not usable as route filters:\n" + errors.joinToString("\n")
        }
        return aliasMethods
    }

    /**
     * `getDeclaredMethod` rather than `getMethod` on purpose: a static method is inherited in Java
     * terms, so a subclass would otherwise silently reuse its superclass's alias and delegate to
     * the wrong bean. Each filter declares its own.
     */
    private fun findAliasMethod(type: Class<*>): Method? {
        val method = runCatching { type.getDeclaredMethod(GatewayFilterAlias.METHOD_NAME) }.getOrNull() ?: return null
        if (!Modifier.isStatic(method.modifiers)) return null
        if (!HandlerFilterFunction::class.java.isAssignableFrom(method.returnType)) return null
        ReflectionUtils.makeAccessible(method)
        return method
    }

    /**
     * Calls the alias and checks that what it delegates to is this very bean. Catches a copy-paste
     * slip where `AuthFilter.alias()` returns `beanFilter<TenantFilter>()`, which would otherwise
     * route traffic through the wrong filter with no complaint.
     *
     * Calling the alias here is cheap and side-effect free: it only constructs a
     * [BeanDelegatingFilter], which touches no bean until a request arrives.
     */
    private fun targetMismatch(beanName: String, type: Class<*>, method: Method): String? {
        val filter = runCatching { method.invoke(null) }.getOrElse { ex ->
            val cause = ex.cause ?: ex
            return "${GatewayFilterAlias.METHOD_NAME}() threw ${cause.javaClass.simpleName}: ${cause.message}"
        }
        return when (val target = (filter as? BeanDelegatingFilter)?.target) {
            // A plain filter instance is allowed: the alias just opted out of bean delegation.
            null -> null
            is BeanTarget.ByType ->
                if (target.type.isAssignableFrom(type)) {
                    null
                } else {
                    "${GatewayFilterAlias.METHOD_NAME}() delegates to ${target.type.simpleName}, not to itself"
                }
            is BeanTarget.ByName ->
                if (target.beanName == beanName) {
                    null
                } else {
                    "${GatewayFilterAlias.METHOD_NAME}() delegates to bean '${target.beanName}', not to '$beanName'"
                }
            is BeanTarget.ByFactory<*> -> null
        }
    }
}
