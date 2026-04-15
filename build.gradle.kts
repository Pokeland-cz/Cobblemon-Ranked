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
    // if it is present.
    // If you remove this line, sources will not be generated.
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
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
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
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-networking-api-v1:${project.property("fabric_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // PlaceholderAPI
    modImplementation("eu.pb4:placeholder-api:${project.property("placeholder_api_version")}")

    // Cobblemon
    modImplementation("com.cobblemon:fabric:${project.property("cobblemon_version")}")

    // MySQL 驱动
    implementation("mysql:mysql-connector-java:8.0.33")

    // Database
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    include("org.xerial:sqlite-jdbc:3.45.1.0")

    // Configuration
    implementation("blue.endless:jankson:1.2.3")
    include("blue.endless:jankson:1.2.3")

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.49.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.49.0")
    include("org.jetbrains.exposed:exposed-core:0.49.0")
    include("org.jetbrains.exposed:exposed-jdbc:0.49.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0") // For WebSocket
    include("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.9.0") // For WebSocket
    include("com.squareup.okio:okio:3.9.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
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
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
