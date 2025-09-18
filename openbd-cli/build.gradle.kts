import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(project(":openbd-core"))

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta6")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.tmquan2508.inject.MainKt")
}

tasks.jar {
    enabled = false
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("") 
        mergeServiceFiles()
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

distributions {
    main {
        contents {
            from(tasks.shadowJar.flatMap { it.archiveFile })
        }
    }
}