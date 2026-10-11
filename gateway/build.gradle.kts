plugins {
    // Versions come from settings.gradle.kts. Kotlin 2.2.x (the version Spring Cloud 2025.1.3's
    // BOM manages) cannot target JVM 25: it falls back to 24 and the build then fails on the
    // Java/Kotlin target mismatch. 2.3+ supports 25, so the Kotlin version is pinned there rather
    // than taken from the BOM. Gradle resolves kotlin-stdlib to the highest requested version, so
    // that wins over the BOM's 2.2.21 without any extra configuration.
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Spring Cloud Gateway Server Web MVC template: reference HandlerFilterFunction beans by name from YAML routes"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        // Emits MethodParameters, which gateway argument binding needs for alias functions
        // that take parameters (see "라우트별 파라미터를 받는 필터" in the README).
        javaParameters = true
    }
}

repositories {
    mavenCentral()
}

val springCloudVersion = "2025.1.3"
val wiremockVersion = "3.13.2"

dependencies {
    // Spring Cloud 2025.1.3 pins spring-boot 4.0.8; keep the two platforms in sync.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.8"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion"))

    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // No spring-boot-configuration-processor: it has no KSP support, and kapt is deprecated.
    // GatewayFilterProperties therefore ships without IDE metadata.

    // testImplementation extends implementation, so both platforms above already apply here.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
