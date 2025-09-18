plugins {
    kotlin("jvm")
}

repositories {
    maven { url = uri("https://maven.rikonardo.com/releases") }
}

dependencies {
    implementation(project(":openbd-payload"))

    implementation("com.rikonardo.cafebabe:CafeBabe:1.0.1")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta6")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}