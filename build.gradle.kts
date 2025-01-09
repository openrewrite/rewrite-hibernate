plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Hibernate ORM Migration"

recipeDependencies {
    parserClasspath("jakarta.persistence:jakarta.persistence-api:latest.release")
    parserClasspath("org.hibernate.orm:hibernate-core:6.5.1.Final")
}

val rewriteVersion = "latest.release"
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:8.41.1"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-migrate-java:2.30.1")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:1.24.1")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-gradle")
    testImplementation("org.openrewrite:rewrite-maven")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testRuntimeOnly("javax.xml.bind:jaxb-api:2.3.1")
    testRuntimeOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
}
