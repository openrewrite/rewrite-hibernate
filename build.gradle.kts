plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Hibernate ORM Migration"

recipeDependencies {
    parserClasspath("javax.persistence:javax.persistence-api:latest.release")
    parserClasspath("jakarta.persistence:jakarta.persistence-api:latest.release")
    parserClasspath("org.hibernate:hibernate-annotations:latest.release")
    parserClasspath("org.hibernate.orm:hibernate-core:6.5.1.Final")
    parserClasspath("com.vladmihalcea:hibernate-types-55:2.+")

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

    testRuntimeOnly("javax.xml.bind:jaxb-api:2.3.1")
}