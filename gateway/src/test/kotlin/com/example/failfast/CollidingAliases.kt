package com.example.failfast

import com.example.gateway.framework.filter.beanFilter
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.ServerResponse

/**
 * An alias object that takes a name the gateway already uses. The built-in `addRequestHeader` is
 * itself supplied by a `FilterSupplier` bean, so both land in the operation map under the same key
 * and `findOperation` takes the first whose argument count matches the route. This alias takes no
 * arguments, so it answers `- name: addRequestHeader` while the two-argument built-in still answers
 * `AddRequestHeader=a,b`. The startup check rejects the reuse rather than depending on that.
 */
object CollidingAliases {

    @JvmStatic
    fun addRequestHeader(): HandlerFilterFunction<ServerResponse, ServerResponse> =
        beanFilter("addRequestHeader")
}
