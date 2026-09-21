buildscript {
    dependencies {
        classpath("org.ow2.asm:asm:9.8")
    }
}

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
    id("com.google.cloud.tools.jib") version "3.5.4"
}

group = "com.travio"
version = "0.0.1-SNAPSHOT"
description = "coupon"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jib {
    from { // 무엇을 가지고 image를 만들 것 인지
        image = "eclipse-temurin:25-jre"
        credHelper {
            helper = "desktop"
        }
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
        }
    }
    to { // 산출 결과는 어떻게 세팅할것인지
        image = "coupon-service"
        credHelper {
            helper = "desktop"
        }
        tags = setOf("latest",project.version.toString())
    }
    container { // 컨테이너 설정
        ports = listOf("8080")
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}