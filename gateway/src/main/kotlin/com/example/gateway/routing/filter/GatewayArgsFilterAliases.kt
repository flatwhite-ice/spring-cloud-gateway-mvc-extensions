package com.example.gateway.routing.filter

import com.example.gateway.framework.filter.factoryFilter
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.ServerResponse

/**
 * Hand-written aliases, for the one thing [com.example.gateway.framework.filter.GatewayFilterBean] cannot express: a filter that takes
 * **per-route arguments**.
 *
 * Filters that take no arguments belong on the bean itself — annotate it [com.example.gateway.framework.filter.GatewayFilterBean] and
 * give its companion object a [com.example.gateway.framework.filter.GatewayFilterAlias] implementation. Nothing needs to be added here.
 *
 * Rules for the functions in here, since the gateway resolves them by method name:
 * - declared in an `object` and annotated [JvmStatic], returning
 *   `HandlerFilterFunction<ServerResponse, ServerResponse>`
 * - **function name == YAML filter name** (matched after `StringUtils.uncapitalize`, so
 *   `requireUser` and `RequireUser` both work)
 * - parameter names are the YAML `args` keys, which is why `javaParameters` is on in the build
 *
 * [com.example.gateway.framework.filter.validation.GatewayFilterValidator] fails startup if a function here is not static, if its name
 * collides with a built-in filter, or if a no-argument alias points at a bean that is missing.
 *
 * @see com.example.gateway.framework.filter.registry.BeanAliasFilterSupplier
 */
object GatewayArgsFilterAliases {

    /**
     * ```yaml
     * filters:
     *   - name: requireUser
     *     args:
     *       mode: strict      # or lenient
     * ```
     */
    @JvmStatic
    fun requireUser(mode: String): HandlerFilterFunction<ServerResponse, ServerResponse> =
        factoryFilter<UserRequirementFilterFactory>("requireUser") { it.create(mode) }
}
