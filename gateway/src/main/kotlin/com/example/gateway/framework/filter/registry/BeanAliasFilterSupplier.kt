package com.example.gateway.framework.filter.registry

import org.springframework.cloud.gateway.server.mvc.filter.SimpleFilterSupplier

/**
 * Publishes a class of hand-written alias functions to the gateway, for filters that take per-route
 * arguments — the one thing [com.example.gateway.framework.filter.GatewayFilterBean] cannot express.
 *
 * Registered as a `@Bean`, which works because `FilterAutoConfiguration` registers a
 * `FilterBeanFactoryDiscoverer` and `BeanFactoryGatewayDiscoverer.loadSuppliers()` resolves
 * suppliers through `beanFactory.getBeanProvider(FilterSupplier::class.java)`. No
 * `META-INF/spring.factories` entry is needed — on the pinned 5.0.3 the gateway registers none of
 * its own that way either, the built-in suppliers are all `@Bean`s.
 *
 * The alias class is always passed in: the framework must not know which aliases an application
 * happens to declare. Applications register their own supplier bean, and
 * [com.example.gateway.framework.filter.validation.GatewayFilterValidator] picks up every one of them. Subclass this to add a
 * second alias class, for example to keep conditional filters behind a `@Conditional`
 * configuration.
 */
open class BeanAliasFilterSupplier(aliasClass: Class<*>) : SimpleFilterSupplier(aliasClass)
