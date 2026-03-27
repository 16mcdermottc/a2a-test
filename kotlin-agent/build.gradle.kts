plugins {
    application
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("com.a2a.kotlinagent.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.3.2")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.2")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.2")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}
