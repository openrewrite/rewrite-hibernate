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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class ChangeHibernateDialectTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.MigrateDialect");
    }

    @ParameterizedTest
    @CsvSource({
      "MySQL5Dialect, MySQLDialect",
      "MySQL55Dialect, MySQLDialect",
      "MySQL57Dialect, MySQLDialect",
      "MySQL8Dialect, MySQLDialect",
      "MariaDB53Dialect, MariaDBDialect",
      "MariaDB10Dialect, MariaDBDialect",
      "MariaDB102Dialect, MariaDBDialect",
      "MariaDB103Dialect, MariaDBDialect",
      "PostgreSQL81Dialect, PostgreSQLDialect",
      "PostgreSQL82Dialect, PostgreSQLDialect",
      "PostgreSQL9Dialect, PostgreSQLDialect",
      "PostgreSQL91Dialect, PostgreSQLDialect",
      "PostgreSQL92Dialect, PostgreSQLDialect",
      "PostgreSQL93Dialect, PostgreSQLDialect",
      "PostgreSQL94Dialect, PostgreSQLDialect",
      "PostgreSQL95Dialect, PostgreSQLDialect",
      "PostgreSQL10Dialect, PostgreSQLDialect",
      "Oracle8iDialect, OracleDialect",
      "Oracle9iDialect, OracleDialect",
      "Oracle10gDialect, OracleDialect",
      "Oracle12cDialect, OracleDialect",
      "SQLServer2005Dialect, SQLServerDialect",
      "SQLServer2008Dialect, SQLServerDialect",
      "SQLServer2012Dialect, SQLServerDialect",
      "SQLServer2016Dialect, SQLServerDialect",
      "DB297Dialect, DB2Dialect",
      "DB2390Dialect, DB2Dialect",
      "DB2390V8Dialect, DB2Dialect",
      "Sybase11Dialect, SybaseASEDialect",
      "SybaseASE15Dialect, SybaseASEDialect",
      "SybaseASE157Dialect, SybaseASEDialect",
      "CockroachDB192Dialect, CockroachDialect",
      "CockroachDB201Dialect, CockroachDialect"
    })
    void preservesAllExistingMappingsInBothXmlForms(String oldDialect, String newDialect) {
        String persistence = """
          <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
              <persistence-unit name="example">
                  <properties>
                      <property name="hibernate.dialect" value="org.hibernate.dialect.%s"/>
                  </properties>
              </persistence-unit>
          </persistence>
          """.formatted(oldDialect);
        String hibernate = """
          <hibernate-configuration>
              <session-factory>
                  <property name="hibernate.dialect">org.hibernate.dialect.%s</property>
                  <property name="dialect">org.hibernate.dialect.%s</property>
              </session-factory>
          </hibernate-configuration>
          """.formatted(oldDialect, oldDialect);
        rewriteRun(spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
          xml(persistence, persistence.replace(oldDialect, newDialect),
            spec -> spec.path("src/main/resources/META-INF/persistence.xml")),
          xml(hibernate, hibernate.replace(oldDialect, newDialect),
            spec -> spec.path("src/main/resources/hibernate.cfg.xml"))
        );
    }

    @ParameterizedTest
    @CsvSource({
      "http://java.sun.com/xml/ns/persistence, 2.0",
      "http://xmlns.jcp.org/xml/ns/persistence, 2.2",
      "https://jakarta.ee/xml/ns/persistence, 3.0",
      "https://jakarta.ee/xml/ns/persistence, 3.1",
      "https://jakarta.ee/xml/ns/persistence, 3.2"
    })
    void changesOnlyDialectInPrefixedPersistenceDescriptors(String namespace, String version) {
        String before = """
          <p:persistence xmlns:p="%s" version="%s"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="urn:other other.xsd %s persistence.xsd">
              <p:persistence-unit name="example">
                  <p:properties>
                      <p:property name="hibernate.dialect" value='org.hibernate.dialect.MySQL8Dialect'/>
                  </p:properties>
              </p:persistence-unit>
          </p:persistence>
          """.formatted(namespace, version, namespace);
        rewriteRun(
          xml(before, before.replace("MySQL8Dialect", "MySQLDialect"),
            spec -> spec.path("src/main/resources/META-INF/persistence.xml"))
        );
    }

    @Test
    void preservesHibernateDoctypeWhitespaceAndCdata() {
        String before = """
          <?xml version="1.0"?>
          <!DOCTYPE hibernate-configuration PUBLIC
                  "-//Hibernate/Hibernate Configuration DTD 3.0//EN"
                  "http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd">
          <hibernate-configuration>
              <session-factory>
                  <!-- Keep the explanatory comment and surrounding whitespace. -->
                  <property name="hibernate.dialect">
                      org.hibernate.dialect.PostgreSQL95Dialect
                  </property>
                  <property name="dialect"><![CDATA[org.hibernate.dialect.MySQL8Dialect]]></property>
              </session-factory>
          </hibernate-configuration>
          """;
        rewriteRun(
          xml(before, before.replace("PostgreSQL95Dialect", "PostgreSQLDialect")
              .replace("MySQL8Dialect", "MySQLDialect"), spec -> spec.path("hibernate.cfg.xml"))
        );
    }

    @Test
    void migratesPrefixedHibernateConfiguration() {
        String before = """
          <h:hibernate-configuration xmlns:h="http://www.hibernate.org/xsd/orm/cfg">
              <h:session-factory>
                  <h:property name="dialect">org.hibernate.dialect.MySQL8Dialect</h:property>
              </h:session-factory>
          </h:hibernate-configuration>
          """;
        rewriteRun(
          xml(before, before.replace("MySQL8Dialect", "MySQLDialect"),
            spec -> spec.path("src/main/resources/hibernate.cfg.xml"))
        );
    }

    @Test
    void migratesUnqualifiedPersistenceAndMultipleUnits() {
        String before = """
          <persistence version="1.0">
              <persistence-unit name="first">
                  <properties>
                      <property name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                  </properties>
              </persistence-unit>
              <persistence-unit name="second">
                  <properties>
                      <property name="hibernate.dialect" value="org.hibernate.dialect.Oracle12cDialect"/>
                      <property name="unrelated" value="org.hibernate.dialect.MySQL8Dialect"/>
                  </properties>
              </persistence-unit>
          </persistence>
          """;
        rewriteRun(
          xml(before, before.replace("name=\"hibernate.dialect\" value=\"org.hibernate.dialect.MySQL8Dialect\"",
                "name=\"hibernate.dialect\" value=\"org.hibernate.dialect.MySQLDialect\"")
              .replace("Oracle12cDialect", "OracleDialect"), spec -> spec.path("persistence.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"org.hibernate.dialect.MySQLDialect", "com.example.MySQL8Dialect",
      "org.hibernate.dialect.MySQL8DialectCustom", "${hibernate.dialect}"})
    void preservesCurrentCustomAndIndirectDialects(String dialect) {
        rewriteRun(
          xml("""
            <persistence>
                <persistence-unit name="example">
                    <properties>
                        <property name="hibernate.dialect" value="%s"/>
                    </properties>
                </persistence-unit>
            </persistence>
            """.formatted(dialect), spec -> spec.path("META-INF/persistence.xml")),
          xml("""
            <hibernate-configuration>
                <session-factory>
                    <property name="hibernate.dialect">%s</property>
                    <property name="dialect">%s</property>
                </session-factory>
            </hibernate-configuration>
            """.formatted(dialect, dialect), spec -> spec.path("hibernate.cfg.xml"))
        );
    }

    @Test
    void preservesUnrelatedKeysAndLocations() {
        rewriteRun(
          xml("""
            <persistence>
                <property name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                <persistence-unit name="example">
                    <property name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                    <properties>
                        <property name="dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                        <property name="custom.hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                        <property name="hibernate.dialect">org.hibernate.dialect.MySQL8Dialect</property>
                        <property value="org.hibernate.dialect.MySQL8Dialect"/>
                    </properties>
                </persistence-unit>
            </persistence>
            """, spec -> spec.path("persistence.xml")),
          xml("""
            <hibernate-configuration>
                <property name="hibernate.dialect">org.hibernate.dialect.MySQL8Dialect</property>
                <session-factory>
                    <property name="custom.hibernate.dialect">org.hibernate.dialect.MySQL8Dialect</property>
                    <property name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                    <property name="hibernate.dialect"><nested>org.hibernate.dialect.MySQL8Dialect</nested></property>
                    <property>org.hibernate.dialect.MySQL8Dialect</property>
                </session-factory>
            </hibernate-configuration>
            """, spec -> spec.path("hibernate.cfg.xml"))
        );
    }

    @Test
    void preservesUnrelatedFilesAndRoots() {
        String persistence = """
          <persistence>
              <persistence-unit name="example">
                  <properties>
                      <property name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                  </properties>
              </persistence-unit>
          </persistence>
          """;
        String hibernate = """
          <hibernate-configuration>
              <session-factory>
                  <property name="hibernate.dialect">org.hibernate.dialect.MySQL8Dialect</property>
              </session-factory>
          </hibernate-configuration>
          """;
        rewriteRun(
          xml(persistence, spec -> spec.path("META-INF/custom-persistence.xml")),
          xml(hibernate, spec -> spec.path("hibernate.custom.xml")),
          xml(hibernate, spec -> spec.path("persistence.xml")),
          xml(persistence, spec -> spec.path("hibernate.cfg.xml"))
        );
    }

    @Test
    void preservesForeignNamespaces() {
        rewriteRun(
          xml("""
            <persistence xmlns="urn:unrelated">
                <persistence-unit name="example">
                    <properties>
                        <property name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                    </properties>
                </persistence-unit>
            </persistence>
            """, spec -> spec.path("META-INF/persistence.xml")),
          xml("""
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence">
                <persistence-unit name="example">
                    <properties>
                        <property xmlns="urn:unrelated" name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                    </properties>
                </persistence-unit>
            </persistence>
            """, spec -> spec.path("other/persistence.xml")),
          xml("""
            <hibernate-configuration xmlns="urn:unrelated">
                <session-factory>
                    <property name="hibernate.dialect">org.hibernate.dialect.MySQL8Dialect</property>
                </session-factory>
            </hibernate-configuration>
            """, spec -> spec.path("hibernate.cfg.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " xmlns:xml=\"http://www.w3.org/XML/1998/namespace\""})
    void preservesReservedXmlNamespaceInPersistence(String declaration) {
        rewriteRun(
          xml("""
            <xml:persistence%s>
                <xml:persistence-unit>
                    <xml:properties>
                        <xml:property name="hibernate.dialect" value="org.hibernate.dialect.MySQL8Dialect"/>
                    </xml:properties>
                </xml:persistence-unit>
            </xml:persistence>
            """.formatted(declaration), spec -> spec.path("META-INF/persistence.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " xmlns:xml=\"http://www.w3.org/XML/1998/namespace\""})
    void preservesReservedXmlNamespaceInHibernateConfiguration(String declaration) {
        rewriteRun(
          xml("""
            <xml:hibernate-configuration%s>
                <xml:session-factory>
                    <xml:property name="hibernate.dialect">org.hibernate.dialect.MySQL8Dialect</xml:property>
                </xml:session-factory>
            </xml:hibernate-configuration>
            """.formatted(declaration), spec -> spec.path("hibernate.cfg.xml"))
        );
    }

    @Test
    void preservesExistingJavaPropertiesAndYamlBehavior() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
            "package org.hibernate.dialect; public class MySQL8Dialect {}")),
          java("""
            import org.hibernate.dialect.MySQL8Dialect;

            class Example {
                MySQL8Dialect dialect;
                String literal = "org.hibernate.dialect.MySQL8Dialect";
            }
            """,
            """
            import org.hibernate.dialect.MySQLDialect;

            class Example {
                MySQLDialect dialect;
                String literal = "org.hibernate.dialect.MySQL8Dialect";
            }
            """),
          properties("spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect",
            "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
            spec -> spec.path("src/main/resources/application.properties")),
          yaml("spring:\n  jpa:\n    database-platform: org.hibernate.dialect.MySQL8Dialect",
            "spring:\n  jpa:\n    database-platform: org.hibernate.dialect.MySQLDialect",
            spec -> spec.path("src/main/resources/application.yml"))
        );
    }
}
