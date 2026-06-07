plugins {
    java
    id("com.gradleup.shadow") version "8.3.8"
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "dev.toxi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    compileOnly("org.jetbrains:annotations:24.1.0")
    runtimeOnly("org.postgresql:postgresql:42.7.4")
    runtimeOnly("com.mysql:mysql-connector-j:9.0.0")
    implementation("net.dv8tion:JDA:5.0.0") {
        exclude(module = "opus-java")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")

        val prefix = "dev.toxi.twofa.libs"
        relocate("com.zaxxer.hikari", "$prefix.hikari")
        relocate("net.dv8tion.jda", "$prefix.jda")
        relocate("org.apache.commons", "$prefix.commons")
        relocate("okhttp3", "$prefix.okhttp")
        relocate("okio", "$prefix.okio")
        relocate("com.neovisionaries", "$prefix.neovisionaries")
        relocate("com.fasterxml.jackson", "$prefix.jackson")
        relocate("gnu.trove", "$prefix.trove")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
