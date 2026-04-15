import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("net.neoforged.moddev") version "2.0.136"
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
    withSourcesJar()
}

sourceSets {
    named("main") {
        resources.srcDir("src/client/resources")
    }
}

repositories {
    mavenCentral()
    maven {
        name = "Aliyun Public"
        url = uri("https://maven.aliyun.com/repository/public")
    }
    maven {
        name = "BMCLAPI Mirror"
        url = uri("https://bmclapi2.bangbang93.com/maven")
    }
    maven {
        name = "NeoForged China Mirror"
        url = uri("https://mirrors.imucraft.cn/neoforge/releases")
        content {
            includeGroup("net.neoforged")
            includeGroupByRegex("net\\.minecraftforge.*")
            includeGroup("cpw.mods")
        }
    }
    maven {
        name = "NeoForged"
        url = uri("https://maven.neoforged.net/releases")
        content {
            includeGroup("net.neoforged")
            includeGroupByRegex("net\\.minecraftforge.*")
            includeGroup("cpw.mods")
        }
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content {
            includeGroup("thedarkcolour")
        }
    }
}

neoForge {
    version = project.property("neo_version") as String

    mods {
        create("cobblemon_ranked") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
        }
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${project.property("kff_version")}")
    implementation("maven.modrinth:cobblemon:${project.property("cobblemon_modrinth_version_id")}")

    implementation("mysql:mysql-connector-java:8.0.33")

    fun embedded(notation: String) {
        implementation(notation)
        add("jarJar", notation)
    }

    embedded("org.xerial:sqlite-jdbc:3.45.1.0")
    embedded("blue.endless:jankson:1.2.3")
    embedded("org.jetbrains.exposed:exposed-core:0.49.0")
    embedded("org.jetbrains.exposed:exposed-jdbc:0.49.0")
    embedded("com.squareup.okhttp3:okhttp:4.12.0")
    embedded("com.squareup.okio:okio:3.9.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("neo_version", project.property("neo_version"))
    inputs.property("kff_loader_version_range", project.property("kff_loader_version_range"))
    filteringCharset = "UTF-8"

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(
            "version" to project.version,
            "minecraft_version" to (project.property("minecraft_version") as String),
            "neo_version" to (project.property("neo_version") as String),
            "cobblemon_version" to (project.property("cobblemon_version") as String),
            "kff_loader_version_range" to (project.property("kff_loader_version_range") as String)
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("../LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
    manifest {
        attributes(
            mapOf(
                "MixinConfigs" to "cobblemon_ranked.mixins.json"
            )
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
}
