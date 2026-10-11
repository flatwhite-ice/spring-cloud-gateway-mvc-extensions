package com.example.downstream

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Dummy routing target for the gateway.
 *
 * Listens on the port the gateway's `downstream.url` default points at (8081) and echoes every
 * request back, so you can see exactly what the route's filters forwarded. It is a development
 * aid only — the automated tests stub the downstream with WireMock.
 */
@SpringBootApplication
class DownstreamApplication

fun main(args: Array<String>) {
    runApplication<DownstreamApplication>(*args)
}
