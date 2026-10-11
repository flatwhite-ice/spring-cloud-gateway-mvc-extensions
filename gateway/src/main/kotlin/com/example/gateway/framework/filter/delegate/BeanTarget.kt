package com.example.gateway.framework.filter.delegate

import org.springframework.beans.factory.BeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.ServerResponse

/**
 * How a [BeanDelegatingFilter] finds the filter it delegates to.
 *
 * Internal: filter authors reach this through `beanFilter` / `factoryFilter`, and the registry and
 * the validator read it back to check that an alias points where it should.
 */
internal sealed interface BeanTarget {

    /** Name used in logs before the bean has been resolved. */
    val label: String

    fun resolve(beanFactory: BeanFactory): HandlerFilterFunction<ServerResponse, ServerResponse>

    /** Name used in logs once the context is available; the bean name where one is known. */
    fun describe(context: ApplicationContext): String = label

    data class ByName(val beanName: String) : BeanTarget {

        override val label get() = beanName

        @Suppress("UNCHECKED_CAST")
        override fun resolve(beanFactory: BeanFactory) =
            beanFactory.getBean(beanName, HandlerFilterFunction::class.java)
                as HandlerFilterFunction<ServerResponse, ServerResponse>
    }

    data class ByType(val type: Class<*>) : BeanTarget {

        override val label get() = type.simpleName

        @Suppress("UNCHECKED_CAST")
        override fun resolve(beanFactory: BeanFactory) =
            beanFactory.getBean(type) as HandlerFilterFunction<ServerResponse, ServerResponse>

        // Logs read better with the name the route used, which is the bean name.
        override fun describe(context: ApplicationContext) =
            context.getBeanNamesForType(type).singleOrNull() ?: label
    }

    data class ByFactory<F : Any>(
        override val label: String,
        val type: Class<F>,
        val build: (F) -> HandlerFilterFunction<ServerResponse, ServerResponse>,
    ) : BeanTarget {

        override fun resolve(beanFactory: BeanFactory) = build(beanFactory.getBean(type))
    }
}
