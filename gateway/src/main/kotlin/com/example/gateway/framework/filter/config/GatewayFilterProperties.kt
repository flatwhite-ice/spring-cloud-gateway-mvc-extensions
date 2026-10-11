package com.example.gateway.framework.filter.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Switches for the startup checks performed by [com.example.gateway.framework.filter.validation.GatewayFilterValidator]. Both default to
 * `true`; turn one off only if it gets in the way.
 */
@ConfigurationProperties("gateway.filter")
class GatewayFilterProperties {

    /**
     * Fail startup when an alias points at a bean that is missing or is not a
     * HandlerFilterFunction.
     */
    var validateBeans: Boolean = true

    /**
     * Fail startup when an alias name collides with a built-in gateway filter name, which would
     * otherwise silently shadow it.
     */
    var failOnNameCollision: Boolean = true
}
