plugins {
    kotlin("jvm") version "1.8.22"
    id("fabric-loom") version "1.2.8"
}

group = "config.plugins"
version = "3.0.0.3"

repositories {
    maven { url = uri("https://maven.fabricmc.net/") }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:1.19.4")
    mappings("net.fabricmc:yarn:1.19.4+build.1:v2") // 明示的にv2つけると安定します
    modImplementation("net.fabricmc:fabric-loader:0.14.21")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.87.2+1.19.4") // 最新のAPIにしてください
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

kotlin {
    jvmToolchain(17)
}