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
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigratePersistenceXmlTest implements RewriteTest {

    @ParameterizedTest
    @ValueSource(strings = {"70", "71"})
    void upgradesPersistenceDescriptorForHibernate7(String target) {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate" + target),
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
}
