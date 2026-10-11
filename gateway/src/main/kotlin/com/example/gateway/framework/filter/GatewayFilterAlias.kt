package com.example.gateway.framework.filter

import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.ServerResponse

/**
 * The contract a [GatewayFilterBean] class's companion object implements so the gateway has a
 * static method to invoke.
 *
 * ```
 * companion object : GatewayFilterAlias {
 *     @JvmStatic override fun alias() = beanFilter<AuthFilter>()
 * }
 * ```
 *
 * Why this exists: the gateway resolves a YAML filter name to a `java.lang.reflect.Method` and
 * invokes it with a `null` target, so the method has to be static. Kotlin and Java cannot declare
 * an abstract static member, so no supertype can force "a static method named X must exist".
 * Implementing this interface on the companion object gets as close as the language allows — the
 * compiler forces [alias] to exist and fixes its name — and [com.example.gateway.framework.filter.registry.GatewayFilterBeanRegistry]
 * keys the filter by the bean name instead of the method name, so every filter can use the same one.
 *
 * [JvmStatic] is the one part the compiler cannot require: without it the override compiles to an
 * instance method on `Companion` and no static method appears on the filter class at all. The
 * registry detects that and fails startup pointing at the class.
 */
interface GatewayFilterAlias {

    /**
     * Returns the filter to install on routes that name this bean. Normally
     * `beanFilter<TheFilter>()`, which resolves the bean on first use so the filter itself can take
     * constructor injection.
     *
     * Naming the filter explicitly keeps the compile-time guarantee that the target really is a
     * `HandlerFilterFunction<ServerResponse, ServerResponse>` — [beanFilter]'s reified type
     * parameter is constrained to it. [com.example.gateway.framework.filter.registry.GatewayFilterBeanRegistry]
     * additionally checks at startup that the target is the bean this alias belongs to.
     */
    fun alias(): HandlerFilterFunction<ServerResponse, ServerResponse>

    companion object {

        /** Name of [alias], fixed by this interface and looked up reflectively. */
        const val METHOD_NAME = "alias"
    }
}
