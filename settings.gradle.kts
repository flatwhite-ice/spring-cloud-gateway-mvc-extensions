pluginManagement {
    // Versions live here so both project build scripts can apply the same plugins without
    // repeating (or disagreeing on) a version.
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.21"
        id("org.jetbrains.kotlin.plugin.spring") version "2.4.21"
        id("org.springframework.boot") version "4.0.8"
    }
}

rootProject.name = "spring-cloud-gateway-mvc-extensions"

// Two sibling projects. The root holds no code: it only aggregates them so one ./gradlew
// builds and runs both. Each is started on its own:
//   ./gradlew :gateway:bootRun      -> :8080
//   ./gradlew :downstream:bootRun   -> :8081
include("gateway")
include("downstream")
