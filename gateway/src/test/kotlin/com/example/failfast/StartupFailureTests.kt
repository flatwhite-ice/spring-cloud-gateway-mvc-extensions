package com.example.failfast

import com.example.gateway.GatewayApplication
import com.example.gateway.framework.filter.registry.BeanAliasFilterSupplier
import com.example.gateway.framework.filter.config.GatewayFilterConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.core.NestedExceptionUtils
import org.springframework.boot.Banner
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/**
 * The point of the whole alias indirection is that mistakes surface at startup rather than on the
 * first request that happens to hit the route. These are the mistakes it catches.
 *
 * Lives outside `com.example.gateway` so the deliberately broken beans here are not picked up by
 * [GatewayApplication]'s component scan. Each case registers its fixture with an explicit `@Bean`,
 * which is enough: `findAnnotationOnBean` reads `@GatewayFilterBean` off the bean's type.
 */
class StartupFailureTests {

    @Test
    fun `a filter bean with no alias method fails startup`() = assertStartupFails(
        NoAliasApp::class.java,
        "not usable as route filters",
        "NoAliasFilter",
        "no static alias() method",
    )

    @Test
    fun `an alias method without JvmStatic fails startup`() = assertStartupFails(
        MissingJvmStaticApp::class.java,
        "MissingJvmStaticFilter",
        // the message has to name the cause, because the compiler accepted this
        "@JvmStatic is what is missing",
    )

    @Test
    fun `annotating something that is not a filter fails startup`() = assertStartupFails(
        NotAFilterApp::class.java,
        "NotAFilter",
        "does not implement HandlerFilterFunction",
    )

    @Test
    fun `an alias that delegates to a different filter fails startup`() = assertStartupFails(
        WrongTargetApp::class.java,
        "delegates to NoAliasFilter, not to itself",
    )

    @Test
    fun `a filter bean named after a built in filter fails startup`() = assertStartupFails(
        CollidingNameApp::class.java,
        "collide with",
        "addRequestHeader",
    )

    @Test
    fun `a hand-written alias name that shadows a built in filter fails startup`() = assertStartupFails(
        CollidingAliasApp::class.java,
        "collide with built-in filters",
        "addRequestHeader",
    )

    @Test
    fun `a hand-written alias pointing at a missing bean fails startup`() = assertStartupFails(
        DanglingAliasApp::class.java,
        "reference beans that do not exist",
        "danglingFilter() -> no bean named 'nobodyRegisteredThis'",
    )

    @Test
    fun `a hand-written alias without JvmStatic fails startup`() = assertStartupFails(
        MissingJvmStaticAliasApp::class.java,
        "must be static",
        "someFilter() is not static",
    )

    /**
     * With no routes configured the gateway never builds one, so
     * [com.example.gateway.framework.filter.registry.AnnotatedFilterBeanDiscoverer] is never asked and
     * the check has to come from the validator instead. This is why the two share
     * [com.example.gateway.framework.filter.registry.GatewayFilterBeanRegistry].
     */
    @Test
    fun `a broken filter bean still fails startup when no routes are configured`() = assertStartupFails(
        NoAliasApp::class.java,
        profile = "no-routes",
        messages = arrayOf("not usable as route filters", "NoAliasFilter"),
    )

    /**
     * Nothing in this project implements this check: `routerFunctionMapping` collects the
     * `RouterFunction` beans during refresh, which forces the gateway to build every configured
     * route, so an unknown filter name already fails the context before the port opens. This test
     * exists so that an upgrade which made route building lazy would be noticed — at which point the
     * validator needs to force the build itself.
     */
    @Test
    fun `a yaml route naming an unknown filter fails startup before the web server starts`() {
        val thrown = catchThrowable { startApp(GatewayApplication::class.java, "routes-unknown-filter") }
        assertThat(NestedExceptionUtils.getMostSpecificCause(thrown!!))
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Unable to find operation")
            .hasMessageContaining("noSuchFilter")
    }

    /**
     * Whether the failure surfaces from the discoverer (wrapped in a `BeanCreationException` while a
     * route is being built) or straight from the validator depends on bean ordering, so the
     * assertion is on the most specific cause. The contract being pinned down is the message.
     */
    private fun assertStartupFails(app: Class<*>, vararg messages: String, profile: String = "failfast") {
        val thrown = catchThrowable { startApp(app, profile) }
        assertThat(thrown).describedAs("startup of %s should have failed", app.simpleName).isNotNull()
        assertThat(NestedExceptionUtils.getMostSpecificCause(thrown!!))
            .isInstanceOf(IllegalStateException::class.java)
            .satisfies({ cause -> messages.forEach { assertThat(cause).hasMessageContaining(it) } })
    }

    private fun startApp(app: Class<*>, vararg profiles: String) {
        val builder = SpringApplicationBuilder(app)
            .properties("server.port=0")
            .bannerMode(Banner.Mode.OFF)
        if (profiles.isNotEmpty()) {
            builder.profiles(*profiles)
        }
        // Only reached when startup unexpectedly succeeds; close so the port is released.
        builder.run().close()
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(GatewayFilterConfiguration::class)
    abstract class FrameworkOnlyApp

    class NoAliasApp : FrameworkOnlyApp() {
        @Bean fun noAliasFilter() = NoAliasFilter()
    }

    class MissingJvmStaticApp : FrameworkOnlyApp() {
        @Bean fun missingJvmStaticFilter() = MissingJvmStaticFilter()
    }

    class NotAFilterApp : FrameworkOnlyApp() {
        @Bean fun notAFilter() = NotAFilter()
    }

    class WrongTargetApp : FrameworkOnlyApp() {
        @Bean fun wrongTargetFilter() = WrongTargetFilter()
    }

    /** The bean name is what collides, so the method name is the point of this fixture. */
    class CollidingNameApp : FrameworkOnlyApp() {
        @Bean fun addRequestHeader() = CollidingNameFilter()
    }

    class CollidingAliasApp : FrameworkOnlyApp() {
        @Bean fun collidingAliasSupplier() = BeanAliasFilterSupplier(CollidingAliases::class.java)
    }

    class DanglingAliasApp : FrameworkOnlyApp() {
        @Bean fun danglingAliasSupplier() = BeanAliasFilterSupplier(DanglingAliases::class.java)
    }

    class MissingJvmStaticAliasApp : FrameworkOnlyApp() {
        @Bean fun missingJvmStaticAliasSupplier() = BeanAliasFilterSupplier(MissingJvmStaticAliases::class.java)
    }
}
