import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    kotlin("jvm") version "1.6.20"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.tmquan2508"
version = "1.1.0"

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
    implementation("org.apache.commons:commons-lang3:3.12.0")
}

// java {
//     toolchain.languageVersion.set(JavaLanguageVersion.of(16))
// }

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}

application {
    mainClass.set("com.tmquan2508.inject.MainKt")
}