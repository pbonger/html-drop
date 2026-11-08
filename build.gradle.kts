plugins {
    id("org.jetbrains.intellij.platform") version "2.3.0"
    kotlin("jvm") version "2.0.0"
}

group = "pim"
version = "1.0.9"

// Values shared with the worker and the macOS app
val sharedSettings = groovy.json.JsonSlurper().parse(file("settings.json")) as Map<*, *>

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))
    intellijPlatform {
        webstorm("2024.3")
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false
    pluginConfiguration {
        id = sharedSettings["pluginId"] as String
    }
}
