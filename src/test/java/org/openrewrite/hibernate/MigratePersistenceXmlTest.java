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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigratePersistenceXmlTest implements RewriteTest {

    @ParameterizedTest
    @ValueSource(strings = {"70", "71"})
    void upgradesPersistenceDescriptorForHibernate7(String target) {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate" + target)
            .cycles(3).expectedCyclesThatMakeChanges(1),
          xml(
            """
            <persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
                         version="2.2">
                <persistence-unit name="example" transaction-type="RESOURCE_LOCAL">
                    <class>example.Account</class>
                </persistence-unit>
            </persistence>
            """,
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_2.xsd"
                         version="3.2">
                <persistence-unit name="example" transaction-type="RESOURCE_LOCAL">
                    <class>example.Account</class>
                </persistence-unit>
            </persistence>
            """,
            spec -> spec.path("src/main/resources/META-INF/persistence.xml")
          )
        );
    }

    @ParameterizedTest
    @CsvSource({"70, 3.0", "70, 3.1", "71, 3.0", "71, 3.1"})
    void upgradesJakartaDescriptors(String target, String version) {
        String before = """
          <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_%s.xsd"
                       version="%s"/>
          """.formatted(version.replace('.', '_'), version);
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate" + target)
            .cycles(3).expectedCyclesThatMakeChanges(1),
          xml(before, before.replace("persistence_" + version.replace('.', '_'), "persistence_3_2")
              .replace("version=\"" + version + "\"", "version=\"3.2\""),
            spec -> spec.path("persistence.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"70", "71"})
    void preservesPrefixesAndOtherSchemaLocations(String target) {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate" + target)
            .cycles(3).expectedCyclesThatMakeChanges(1),
          xml(
            """
            <p:persistence xmlns:p="https://jakarta.ee/xml/ns/persistence"
                           xmlns:schema="http://www.w3.org/2001/XMLSchema-instance"
                           schema:schemaLocation="urn:before before.xsd
                                                 https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_1.xsd urn:after after.xsd"
                           version="3.1">
                <p:persistence-unit name="one"/>
                <p:persistence-unit name="two"/>
            </p:persistence>
            """,
            """
            <p:persistence xmlns:p="https://jakarta.ee/xml/ns/persistence"
                           xmlns:schema="http://www.w3.org/2001/XMLSchema-instance"
                           schema:schemaLocation="urn:before before.xsd
                                                 https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_2.xsd urn:after after.xsd"
                           version="3.2">
                <p:persistence-unit name="one"/>
                <p:persistence-unit name="two"/>
            </p:persistence>
            """,
            spec -> spec.path("module/src/main/resources/META-INF/persistence.xml")
          )
        );
    }

    @ParameterizedTest
    @CsvSource({"70, 3.2", "70, 3.3", "70, 4.0", "71, 3.2", "71, 3.3", "71, 4.0"})
    void preservesCurrentAndNewerDescriptors(String target, String version) {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate" + target)
            .cycles(3).expectedCyclesThatMakeChanges(0),
          xml("""
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_%s.xsd"
                         version="%s"/>
            """.formatted(version.replace('.', '_'), version),
            spec -> spec.path("src/main/resources/META-INF/persistence.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"60", "61", "62", "63", "64", "65", "66", "EE9"})
    void preservesHibernate6AndEe9Contracts(String target) {
        rewriteRun(
          spec -> spec.recipeFromResources("EE9".equals(target) ?
              "org.openrewrite.java.migrate.jakarta.JavaxPersistenceXmlToJakartaPersistenceXml" :
              "org.openrewrite.hibernate.MigrateToHibernate" + target)
            .cycles(3).expectedCyclesThatMakeChanges(1),
          xml(
            """
            <persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
                         version="2.2"/>
            """,
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_0.xsd"
                         version="3.0"/>
            """,
            spec -> spec.path("src/main/resources/META-INF/persistence.xml")
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"70", "71"})
    void doesNotUpgradeAnArbitrarilyNamedXmlFileTo32(String target) {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate" + target)
            .cycles(3).expectedCyclesThatMakeChanges(0),
          xml("""
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0"/>
            """, spec -> spec.path("src/main/resources/custom.xml"))
        );
    }
}
