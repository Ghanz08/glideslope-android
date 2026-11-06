// Pure Kotlin: rules shared with the server (capture, money, later runway; F-14.4). No Android types, plain JVM tests.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
}
