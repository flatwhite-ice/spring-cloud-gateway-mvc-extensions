package com.example.gateway.framework.filter

import com.example.gateway.framework.filter.delegate.BeanDelegatingFilter
import com.example.gateway.framework.filter.delegate.BeanTarget
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.ServerResponse

/**
 * Delegates to the [HandlerFilterFunction] bean of type [T]. The normal body of a
 * [GatewayFilterAlias.alias] override.
 *
 * Resolution is by type rather than by name, so there is no bean-name string to go stale and the
 * compiler checks that [T] really is a gateway filter.
 */
inline fun <reified T : HandlerFilterFunction<ServerResponse, ServerResponse>> beanFilter():
    HandlerFilterFunction<ServerResponse, ServerResponse> = beanFilterOfType(T::class.java)

/**
 * Delegates to the [HandlerFilterFunction] bean registered under [beanName].
 *
 * For hand-written aliases in a [com.example.gateway.framework.filter.registry.BeanAliasFilterSupplier]
 * class, where the YAML name is the method name rather than a bean name. The typed [beanFilter] is
 * preferred everywhere else.
 *
 * None of these are part of a registered `FilterSupplier`, so `- name: beanFilter` is not usable
 * from YAML — mixing `- Bean=authFilter` with `- name: authFilter` would make routes harder to read.
 */
fun beanFilter(beanName: String): HandlerFilterFunction<ServerResponse, ServerResponse> =
    BeanDelegatingFilter(BeanTarget.ByName(beanName))

/**
 * Delegates to a filter built by the factory bean of type [F]. For filters that need both
 * constructor injection and per-route arguments: the alias function takes the arguments and this
 * hands them to the factory.
 *
 * @param name the YAML filter name, i.e. the name of the enclosing alias function. Only used to
 * label the filter in the request trace, which exists to be read against the route's YAML — the
 * factory's class name would not line up.
 */
inline fun <reified F : Any> factoryFilter(
    name: String,
    noinline build: (F) -> HandlerFilterFunction<ServerResponse, ServerResponse>,
): HandlerFilterFunction<ServerResponse, ServerResponse> = factoryFilterOfType(name, F::class.java, build)

/** Non-reified form of [beanFilter], so the inline function above can stay public. */
@PublishedApi
internal fun <T : HandlerFilterFunction<ServerResponse, ServerResponse>> beanFilterOfType(
    type: Class<T>,
): HandlerFilterFunction<ServerResponse, ServerResponse> = BeanDelegatingFilter(BeanTarget.ByType(type))

/** Non-reified form of [factoryFilter], so the inline function above can stay public. */
@PublishedApi
internal fun <F : Any> factoryFilterOfType(
    name: String,
    type: Class<F>,
    build: (F) -> HandlerFilterFunction<ServerResponse, ServerResponse>,
): HandlerFilterFunction<ServerResponse, ServerResponse> =
    BeanDelegatingFilter(BeanTarget.ByFactory(name, type, build))
