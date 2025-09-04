import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.application.CreateStartScripts

import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm") version "1.6.20"
    application
    id("com.gradleup.shadow") version "9.0.0-rc2"
}

version = rootProject.version

repositories {
    mavenCentral()
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://maven.rikonardo.com/releases") }
}

dependencies {
    implementation("org.spigotmc:spigot-api:1.19-R0.1-SNAPSHOT")
    implementation("com.rikonardo.cafebabe:CafeBabe:1.0.1")
    implementation("org.javassist:javassist:3.29.0-GA")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta6")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("com.google.code.gson:gson:2.10.1")
    compileOnly("org.apache.logging.log4j:log4j-api:2.17.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.1")
    compileOnly("org.apache.commons:commons-lang3:3.12.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.tmquan2508.inject.MainKt")
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    named<CreateStartScripts>("startShadowScripts") {
        dependsOn(named("jar"))
    }

    named("distZip") {
        dependsOn(named("shadowJar"))
    }
    named("distTar") {
        dependsOn(named("shadowJar"))
    }

    named("startScripts") {
        dependsOn(named("shadowJar"))
    }

    build {
        dependsOn(shadowJar)
    }
}