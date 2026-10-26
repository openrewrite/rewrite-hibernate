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
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigrateDialectXmlTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate62");
    }

    @DocumentExample
    @Test
    void migratesPersistenceXml() {
        rewriteRun(
          xml(
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
                <persistence-unit name="example">
                    <properties>
                        <property name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                    </properties>
                </persistence-unit>
            </persistence>
            """,
            """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
                <persistence-unit name="example">
                    <properties>
                        <property name="hibernate.dialect" value="org.hibernate.dialect.MySQLDialect"/>
                    </properties>
                </persistence-unit>
            </persistence>
            """,
            spec -> spec.path("src/main/resources/META-INF/persistence.xml")
          )
        );
    }

    @Test
    void migratesHibernateCfgXml() {
        rewriteRun(
          xml(
            """
            <hibernate-configuration>
                <session-factory>
                    <property name="hibernate.dialect">org.hibernate.dialect.PostgreSQL95Dialect</property>
                </session-factory>
            </hibernate-configuration>
            """,
            """
            <hibernate-configuration>
                <session-factory>
                    <property name="hibernate.dialect">org.hibernate.dialect.PostgreSQLDialect</property>
                </session-factory>
            </hibernate-configuration>
            """,
            spec -> spec.path("src/main/resources/hibernate.cfg.xml")
          )
        );
    }
}
