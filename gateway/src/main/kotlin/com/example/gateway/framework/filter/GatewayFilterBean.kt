package com.example.gateway.framework.filter

import org.springframework.stereotype.Component

/**
 * Marks a [org.springframework.web.servlet.function.HandlerFilterFunction] bean as usable from a
 * YAML route, under its own bean name.
 *
 * Meta-annotated with [Component], so the ordinary component scan picks the class up and
 * `AnnotationBeanNameGenerator` derives the bean name the same way it would for any stereotype:
 * `AuthFilter` becomes `authFilter`, which is exactly the name the route refers to. Pass [value]
 * to choose a different name. No custom scanner is involved.
 *
 * The class must also expose the static alias method that the gateway invokes, which is what
 * [GatewayFilterAlias] is for:
 *
 * ```
 * @GatewayFilterBean
 * class AuthFilter(private val introspector: TokenIntrospector) :
 *     HandlerFilterFunction<ServerResponse, ServerResponse> {
 *
 *     override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>) = ...
 *
 *     companion object : GatewayFilterAlias {
 *         @JvmStatic override fun alias() = beanFilter<AuthFilter>()
 *     }
 * }
 * ```
 *
 * ```yaml
 * filters:
 *   - name: authFilter
 * ```
 *
 * @see GatewayFilterAlias
 * @see com.example.gateway.framework.filter.registry.GatewayFilterBeanRegistry
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Component
annotation class GatewayFilterBean(

    /** Bean name, and therefore the YAML filter name. Defaults to the uncapitalized class name. */
    val value: String = "",
)
