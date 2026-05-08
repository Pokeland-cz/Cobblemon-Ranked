import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"
    id("fabric-loom") version "1.9.1"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("cobblemon_ranked") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

repositories {
    // China-friendly mirror for Maven Central and common public artifacts.
    maven {
        name = "Aliyun Public"
        url = uri("https://maven.aliyun.com/repository/public")
    }
    mavenCentral()

    // China-friendly Fabric mirror.
    maven {
        name = "FastMC Fabric Mirror"
        url = uri("https://fabric.fastmcmirror.org")
        content {
            includeGroup("net.fabricmc")
            includeGroup("net.fabricmc.fabric-api")
        }
    }

    // Official fallbacks.
    maven {
        name = "Fabric Official"
        url = uri("https://maven.fabricmc.net/")
        content {
            includeGroup("net.fabricmc")
            includeGroup("net.fabricmc.fabric-api")
        }
    }
    maven {
        name = "Quilt Release"
        url = uri("https://maven.quiltmc.org/repository/release")
    }
    maven {
        name = "Impact Development"
        url = uri("https://maven.impactdev.net/repository/development/")
    }
    maven {
        name = "Nucleoid"
        url = uri("https://maven.nucleoid.xyz/")
        content {
            includeGroup("eu.pb4")
        }
    }
    // Cobblemon currently keeps the official repository as a fallback.
    maven {
        name = "Cable MC Releases"
        url = uri("https://repo.cable-mc.net/releases")
        content {
            includeGroup("com.cobblemon")
        }
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    // Environment setup
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // External Mods
    modImplementation("eu.pb4:placeholder-api:${project.property("placeholder_api_version")}")
    modImplementation("com.cobblemon:fabric:${project.property("cobblemon_version")}")

    // --- Bundled Libraries (Jar-in-Jar) ---
    // Uses a loop to safely apply both implementation() and include() without Kotlin DSL type errors
    listOf(
        "com.mysql:mysql-connector-j:8.4.0",           // MySQL
        "org.mariadb.jdbc:mariadb-java-client:3.5.8",  // MariaDB
        "org.xerial:sqlite-jdbc:3.45.1.0",             // SQLite
        "blue.endless:jankson:1.2.3",                  // Configuration
        "org.jetbrains.exposed:exposed-core:0.49.0",   // Exposed ORM
        "org.jetbrains.exposed:exposed-jdbc:0.49.0",   // Exposed JDBC
        "com.squareup.okhttp3:okhttp:4.12.0",          // OkHttp
        "com.squareup.okio:okio:3.9.0"                 // Okio
    ).forEach { lib ->
        implementation(lib)
        include(lib)
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    inputs.property("kotlin_loader_version", project.property("kotlin_loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to (project.property("minecraft_version") as String),
            "loader_version" to (project.property("loader_version") as String),
            "kotlin_loader_version" to (project.property("kotlin_loader_version") as String)
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.jar {
    from("LICENSE") {
        // Added .get() to archivesName to safely unwrap the property provider
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
    repositories {
        // Publishing repositories setup goes here
    }
}