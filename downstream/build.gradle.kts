plugins {
    // Versions come from settings.gradle.kts.
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Dummy routing target: echoes back what the gateway forwarded"

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.8"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
