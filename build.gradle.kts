plugins {
    base
    id("org.jetbrains.kotlin.jvm") version "1.9.23" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

subprojects {
    group = "com.tmquan2508"
    version = "2.1.0"

    repositories {
        mavenCentral()
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
        maven { url = uri("https://maven.rikonardo.com/releases") }
    }
}

tasks.register<Copy>("gatherJar") {
    dependsOn(project(":openbd-cli").tasks.named("shadowJar"))
    from(project(":openbd-cli").tasks.named("shadowJar"))
    into(layout.buildDirectory.dir("libs"))
    rename { fileName ->
        fileName.replace(project(":openbd-cli").name, rootProject.name)
    }
}

tasks.named("assemble") {
    dependsOn(tasks.named("gatherJar"))
}