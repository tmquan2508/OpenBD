import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow")
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
        mergeServiceFiles()
        archiveClassifier.set("") 
        archiveBaseName.set("OpenBD") 
        archiveVersion.set(project.version.toString())
    }

    build {
        dependsOn(shadowJar)
    }

    distZip { dependsOn(shadowJar) }
    distTar { dependsOn(shadowJar) }
    startScripts { dependsOn(shadowJar) }
}

distributions {
    main {
        contents {
            from(tasks.shadowJar.flatMap { it.archiveFile })
        }
    }
}