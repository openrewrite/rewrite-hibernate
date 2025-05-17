plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Hibernate ORM Migration"

recipeDependencies {
    parserClasspath("jakarta.persistence:jakarta.persistence-api:latest.release")
    parserClasspath("org.hibernate:hibernate-core:5.6.15.Final")
    parserClasspath("org.hibernate.orm:hibernate-core:6.5.1.Final")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-gradle")
    testImplementation("org.openrewrite:rewrite-maven")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testRuntimeOnly("org.hibernate:hibernate-core:5.6.15.Final")
    testRuntimeOnly("javax.persistence:javax.persistence-api:2.2")
    testRuntimeOnly("javax.xml.bind:jaxb-api:2.3.1")
    testRuntimeOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
}
