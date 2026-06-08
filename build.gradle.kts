plugins {
    java
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "dev.toxi"
version = "1.0-SNAPSHOT"

val paperApiVersion = "1.21.1-R0.1-SNAPSHOT"
val hikariVersion = "5.1.0"
val sqliteJdbcVersion = "3.46.1.3"
val jdaVersion = "5.0.0"
val postgresqlVersion = "42.7.4"
val mysqlVersion = "9.0.0"
val annotationsVersion = "24.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
    compileOnly("org.jetbrains:annotations:$annotationsVersion")
    compileOnly("org.postgresql:postgresql:$postgresqlVersion")
    compileOnly("com.mysql:mysql-connector-j:$mysqlVersion")
    compileOnly("net.dv8tion:JDA:$jdaVersion") {
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

    processResources {
        val props = mapOf(
            "version" to version,
            "description" to project.description,
            "hikariVersion" to hikariVersion,
            "sqliteJdbcVersion" to sqliteJdbcVersion,
            "jdaVersion" to jdaVersion,
            "postgresqlVersion" to postgresqlVersion,
            "mysqlVersion" to mysqlVersion,
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
