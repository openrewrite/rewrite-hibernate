/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.hibernate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigratePersistenceXmlToJakartaPersistence32Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigratePersistenceXmlToJakartaPersistence32()).cycles(3);
    }

    @Test
    void preservesOtherPairsOnTheSameLine() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          xml(
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="urn:before before.xsd https://jakarta.ee/xml/ns/persistence old.xsd urn:after after.xsd"
                         version="3.0"/>
            """,
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="urn:before before.xsd https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_2.xsd urn:after after.xsd"
                         version="3.2"/>
            """, spec -> spec.path("META-INF/persistence.xml"))
        );
    }

    @Test
    void preservesAbsentSchemaLocation() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          xml(
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.1"/>
            """,
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.2"/>
            """, spec -> spec.path("persistence.xml"))
        );
    }

    @Test
    void onlyReplacesLocationsInThePersistenceNamespacePosition() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          xml(
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="urn:other https://jakarta.ee/xml/ns/persistence urn:after after.xsd"
                         version="3.0"/>
            """,
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="urn:other https://jakarta.ee/xml/ns/persistence urn:after after.xsd"
                         version="3.2"/>
            """, spec -> spec.path("persistence.xml"))
        );
    }

    @Test
    void leavesMalformedSchemaLocationIntact() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          xml(
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence old.xsd dangling"
                         version="3.1"/>
            """,
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence old.xsd dangling"
                         version="3.2"/>
            """, spec -> spec.path("persistence.xml"))
        );
    }

    @Test
    void leavesNonXsiAndUnqualifiedAttributesIntact() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          xml(
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" xmlns:other="urn:other"
                         schemaLocation="https://jakarta.ee/xml/ns/persistence unqualified.xsd"
                         other:schemaLocation="https://jakarta.ee/xml/ns/persistence unrelated.xsd"
                         other:version="3.0" version="3.0"/>
            """,
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" xmlns:other="urn:other"
                         schemaLocation="https://jakarta.ee/xml/ns/persistence unqualified.xsd"
                         other:schemaLocation="https://jakarta.ee/xml/ns/persistence unrelated.xsd"
                         other:version="3.0" version="3.2"/>
            """, spec -> spec.path("persistence.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.2", "3", "3.01", "3.2", "3.3", "4.0", "unknown"})
    void preservesOtherVersions(String version) {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          xml("""
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="%s"/>
            """.formatted(version), spec -> spec.path("persistence.xml"))
        );
    }

    @Test
    void requiresAnUnqualifiedVersion() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          xml("""
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"/>
            """, spec -> spec.path("one/persistence.xml")),
          xml("""
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" xmlns:other="urn:other" other:version="3.0"/>
            """, spec -> spec.path("two/persistence.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"custom.xml", "META-INF/orm.xml", "persistence.xml.backup"})
    void restrictsSourcePaths(String path) {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          xml("""
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0"/>
            """, spec -> spec.path(path))
        );
    }

    @Test
    void requiresTheRootElementAndItsActualNamespace() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          xml("""
            <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0"/>
            """, spec -> spec.path("one/persistence.xml")),
          xml("""
            <persistence xmlns="urn:other" xmlns:p="https://jakarta.ee/xml/ns/persistence" version="3.0"/>
            """, spec -> spec.path("two/persistence.xml")),
          xml("""
            <p:persistence xmlns="https://jakarta.ee/xml/ns/persistence" xmlns:p="urn:other" version="3.0"/>
            """, spec -> spec.path("three/persistence.xml")),
          xml("""
            <wrapper><persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0"/></wrapper>
            """, spec -> spec.path("four/persistence.xml"))
        );
    }
}
