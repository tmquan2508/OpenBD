import com.tmquan2508.buildtools.AdvancedObfuscationTask
import com.tmquan2508.gradle.RenameMethodsTask

plugins {
    java
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.19-R0.1-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-api:2.17.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.1")
    compileOnly("org.apache.commons:commons-lang3:3.18.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    val mainClassesDir = layout.buildDirectory.dir("classes/java/main")

    val obfuscateAdvanced by register<AdvancedObfuscationTask>("obfuscateAdvanced") {
        group = "build"
        description = "Applies advanced obfuscation (Control Flow Flattening)."
        dependsOn("compileJava")

        targetClass = "com.tmquan2508.exploit.Config"
        classesDir.set(mainClassesDir)
        outputClassesDir.set(mainClassesDir)
    }

    val renameMethods by register<RenameMethodsTask>("renameMethods") {
        group = "build"
        description = "Renames methods in the specified class."
        dependsOn(obfuscateAdvanced)

        targetClass = "com.tmquan2508.exploit.Config"
        classesDir.set(mainClassesDir)
        outputClassesDir.set(mainClassesDir)
    }

    named("jar") {
        dependsOn(renameMethods)
    }
    
    classes {
        dependsOn(renameMethods)
    }
}