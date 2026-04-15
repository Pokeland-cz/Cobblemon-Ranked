pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public") {
            name = "Aliyun Public"
        }
        maven("https://bmclapi2.bangbang93.com/maven") {
            name = "BMCLAPI Mirror"
        }
        maven("https://mirrors.imucraft.cn/neoforge/releases") {
            name = "NeoForged China Mirror"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged"
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Cobblemon-Ranked-NeoForge-1.21.1"
