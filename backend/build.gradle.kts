plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

group = "net.palacesoft.spotifier"
version = "1.0.0"

application {
    mainClass.set("net.palacesoft.spotifier.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.nego)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("spotifier-backend")
    archiveClassifier.set("")
    archiveVersion.set("")
}
