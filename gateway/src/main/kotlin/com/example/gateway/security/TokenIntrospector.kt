package com.example.gateway.security

import org.springframework.stereotype.Component

/**
 * Stand-in for a real token introspection call. Exists mainly to show that filter beans can take
 * constructor injection, which the static alias functions cannot.
 *
 * Accepts `Bearer opaque-<userId>` and rejects everything else.
 */
@Component
class TokenIntrospector {

    fun resolveUserId(authorizationHeader: String?): String? {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null
        }
        val token = authorizationHeader.removePrefix(BEARER_PREFIX).trim()
        if (!token.startsWith(VALID_TOKEN_PREFIX)) {
            return null
        }
        return token.removePrefix(VALID_TOKEN_PREFIX).ifEmpty { null }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val VALID_TOKEN_PREFIX = "opaque-"
    }
}
