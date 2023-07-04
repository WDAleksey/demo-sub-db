import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports.Binding
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Property
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath("org.testcontainers:postgresql:1.18.3")
        classpath("com.github.docker-java:docker-java:3.3.1")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("org.jetbrains.kotlin.kapt") version "1.8.21"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.8.21"

    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("io.micronaut.library") version "3.7.9"

    id("nu.studer.jooq") version "8.2"
    id("org.flywaydb.flyway") version "9.8.1"
}

micronaut {
    version.set("3.9.1")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("${project.group}.db.*")
    }
}

val image = "postgres:14.4-alpine"
val db = "postgres"
val schema = "public"
val port = 50043
val jdbcDriver = "org.postgresql.Driver"
val jdbcUrl = "jdbc:postgresql://localhost:$port/$db?loggerLevel=OFF"
val jdbcUser = "postgres"
val jdbcPassword = "postgres"
val container = PostgreSQLContainer<Nothing>(DockerImageName.parse(image)).apply {
    // In CI/CD set env 'TESTCONTAINERS_RYUK_DISABLED=true'
    withDatabaseName(db)
    withUsername(jdbcUser)
    withPassword(jdbcPassword)
    withExposedPorts(5432)
    withCreateContainerCmdModifier {
        val portBinding = PortBinding(Binding.bindPort(port), ExposedPort(5432))
        it.withHostConfig(HostConfig().withPortBindings(portBinding))
    }
}

flyway {
    url = jdbcUrl
    user = jdbcUser
    password = jdbcPassword
    schemas = arrayOf(schema)
    locations = arrayOf("filesystem:./src/main/resources/db/migration")
    driver = jdbcDriver
}

jooq {
    version.set("3.18.3")
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                logging = Logging.INFO
                jdbc.apply {
                    driver = jdbcDriver
                    url = jdbcUrl
                    user = jdbcUser
                    password = jdbcPassword
                    properties.add(Property().withKey("ssl").withValue("false"))
                }
                generator.apply {
                    name = "org.jooq.codegen.DefaultGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = schema
                        forcedTypes.addAll(listOf(
                            ForcedType().apply {
                                name = "varchar"
                                includeExpression = ".*"
                                includeTypes = "JSONB?"
                            },
                            ForcedType().apply {
                                name = "varchar"
                                includeExpression = ".*"
                                includeTypes = "INET"
                            }
                        ))
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = true
                        isFluentSetters = true
                    }
                    target.apply {
                        packageName = "${project.group}.db.jooq"
                        directory = "build/generated/jooq/main"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))

    jooqGenerator("org.postgresql:postgresql:42.6.0")

    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")

    api("io.micronaut.flyway:micronaut-flyway")
    api("org.postgresql:postgresql:42.6.0")

    implementation("jakarta.annotation:jakarta.annotation-api")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:testcontainers")
}

java.sourceCompatibility = JavaVersion.toVersion("17")

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}

tasks.flywayMigrate.configure {
    finalizedBy("generateJooq")
    doFirst { container.start() }
}

tasks.named("generateJooq").configure {
    dependsOn(tasks.flywayMigrate)
    doLast { container.stop() }
}
