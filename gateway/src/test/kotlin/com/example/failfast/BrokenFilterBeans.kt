package com.example.failfast

import com.example.gateway.framework.filter.GatewayFilterAlias
import com.example.gateway.framework.filter.GatewayFilterBean
import com.example.gateway.framework.filter.beanFilter
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

/** Passes the request through; the fixtures below only care about their declarations. */
private fun passThrough(request: ServerRequest, next: HandlerFunction<ServerResponse>) = next.handle(request)

/** Annotated, but never given a companion object, so there is no static method to invoke. */
@GatewayFilterBean
class NoAliasFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {
    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>) = passThrough(request, next)
}

/**
 * The one mistake the compiler cannot catch: the companion object implements the contract, but
 * without [JvmStatic] the override stays an instance method on `Companion` and no static method
 * appears on the filter class.
 */
@GatewayFilterBean
class MissingJvmStaticFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>) = passThrough(request, next)

    companion object : GatewayFilterAlias {
        override fun alias() = beanFilter<MissingJvmStaticFilter>()
    }
}

/** Annotated but not a filter at all. */
@GatewayFilterBean
class NotAFilter

/** Copy-paste slip: delegates to a different filter than the bean it is registered as. */
@GatewayFilterBean
class WrongTargetFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>) = passThrough(request, next)

    companion object : GatewayFilterAlias {
        @JvmStatic
        override fun alias() = beanFilter<NoAliasFilter>()
    }
}

/** Registered under a name the gateway already uses, which would shadow the built-in filter. */
@GatewayFilterBean
class CollidingNameFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>) = passThrough(request, next)

    companion object : GatewayFilterAlias {
        @JvmStatic
        override fun alias() = beanFilter<CollidingNameFilter>()
    }
}

/** Hand-written alias pointing at a bean nobody registered. */
object DanglingAliases {

    @JvmStatic
    fun danglingFilter(): HandlerFilterFunction<ServerResponse, ServerResponse> =
        beanFilter("nobodyRegisteredThis")
}

/** Hand-written alias missing [JvmStatic], so the gateway would get an instance method. */
object MissingJvmStaticAliases {

    fun someFilter(): HandlerFilterFunction<ServerResponse, ServerResponse> =
        beanFilter("someFilter")
}
