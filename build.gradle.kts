import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.6.0"
val jacksonVersion = "2.13.2"
val jacksonPatchVersion = "2.13.2.1"
val jacksonBomVersion = "2.13.2.20220324"
val kluentVersion = "1.68"
val ktorVersion = "1.6.8"
val logbackVersion = "1.2.11"
val logstashEncoderVersion = "7.0.1"
val prometheusVersion = "0.15.0"
val spekVersion = "2.0.18"
val smCommonVersion = "1.a92720c"
val mockkVersion = "1.12.3"
val nimbusdsVersion = "9.21"
val testContainerKafkaVersion = "1.16.3"
val postgresVersion = "42.3.3"
val flywayVersion = "8.5.4"
val hikariVersion = "5.0.1"
val testContainerVersion = "1.16.3"
val kotlinVersion = "1.6.0"

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
}

plugins {
    id("org.jmailen.kotlinter") version "3.6.0"
    kotlin("jvm") version "1.6.0"
    id("com.diffplug.spotless") version "5.16.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    jacoco
}

buildscript {
    dependencies {
    }
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation ("com.fasterxml.jackson.core:jackson-databind:$jacksonPatchVersion")
    implementation ("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")

    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        html.isEnabled = true
    }
}


tasks {

    create("printVersion") {
        println(project.version)
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    withType<JacocoReport> {
        classDirectories.setFrom(
                sourceSets.main.get().output.asFileTree.matching {
                    exclude()
                }
        )

    }
    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }

    "check" {
        dependsOn("formatKotlin")
    }
}
