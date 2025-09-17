import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.toolchain.JavaLanguageVersion

import com.tmquan2508.buildtools.AdvancedObfuscationTask
import com.tmquan2508.gradle.RenameMethodsTask

plugins {
    kotlin("jvm") version "2.2.0"
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
    compileOnly("org.spigotmc:spigot-api:1.19-R0.1-SNAPSHOT")
    implementation("com.rikonardo.cafebabe:CafeBabe:1.0.1")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta6")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    compileOnly("org.apache.logging.log4j:log4j-api:2.17.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.1")
    compileOnly("org.apache.commons:commons-lang3:3.18.0")
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
    val mainClassesDir = layout.buildDirectory.dir("classes/java/main")

    val obfuscateAdvanced by register<AdvancedObfuscationTask>("obfuscateAdvanced") {
        description = "Applies advanced obfuscation (Control Flow Flattening)."
        group = "build"
        dependsOn("compileJava")

        targetClass = "com.tmquan2508.exploit.Config"

        classesDir.set(mainClassesDir)
        outputClassesDir.set(mainClassesDir)
    }

    val renameMethods by register<RenameMethodsTask>("renameMethods") {
        description = "Renames methods in the specified class."
        group = "build"
        dependsOn(obfuscateAdvanced)

        targetClass = "com.tmquan2508.exploit.Config"
        classesDir.set(mainClassesDir)
        outputClassesDir.set(mainClassesDir)
    }

    named("jar") {
        dependsOn(renameMethods)
    }

    if (project.tasks.findByName("inspectClassesForKotlinIC") != null) {
        named("inspectClassesForKotlinIC") {
            dependsOn(renameMethods)
        }
    }

    named<ShadowJar>("shadowJar") {
        dependsOn(renameMethods)
        archiveClassifier.set("")
        mergeServiceFiles()

        val spigotApiJar = project.configurations.getByName("compileClasspath")
            .find { it.name.startsWith("spigot-api") }

        if (spigotApiJar != null) {
            from(zipTree(spigotApiJar)) {
                include("org/bukkit/plugin/Plugin.class")
            }
        }
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