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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class MigrateToHibernate56Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate56")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-5", "javax.persistence-api")
            //language=java
            .dependsOn(
              """
                package org.hibernate.ejb;
                public class HibernatePersistence extends org.hibernate.jpa.HibernatePersistenceProvider {}
                """,
              """
                package org.hibernate.cfg;
                public class AnnotationConfiguration extends Configuration {}
                """,
              """
                package org.hibernate.service;
                import java.util.Map;
                public class ServiceRegistryBuilder {
                    public ServiceRegistryBuilder applySettings(Map settings) { return this; }
                    public ServiceRegistry buildServiceRegistry() { return null; }
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void replaceEntityManagerWithCore() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>a</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-entitymanager</artifactId>
                    <version>4.3.11.Final</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>a</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-core</artifactId>
                    <version>5.6.15.Final</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void mergeEntityManagerIntoDeclaredCore() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>a</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-core</artifactId>
                    <version>5.1.17.Final</version>
                  </dependency>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-entitymanager</artifactId>
                    <version>5.1.17.Final</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>a</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-core</artifactId>
                    <version>5.6.15.Final</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void upgradeOnlyOrmArtifacts() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>a</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-envers</artifactId>
                    <version>5.4.33.Final</version>
                  </dependency>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-tools</artifactId>
                    <version>5.4.33.Final</version>
                  </dependency>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-validator</artifactId>
                    <version>5.4.3.Final</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>a</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-envers</artifactId>
                    <version>5.6.15.Final</version>
                  </dependency>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-tools</artifactId>
                    <version>5.4.33.Final</version>
                  </dependency>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-validator</artifactId>
                    <version>5.4.3.Final</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void replaceRemovedPersistenceProvider() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.ejb.HibernatePersistence;

              class Bootstrap {
                  javax.persistence.spi.PersistenceProvider provider() {
                      return new HibernatePersistence();
                  }
              }
              """,
            """
              import org.hibernate.jpa.HibernatePersistenceProvider;

              class Bootstrap {
                  javax.persistence.spi.PersistenceProvider provider() {
                      return new HibernatePersistenceProvider();
                  }
              }
              """
          ),
          //language=xml
          xml(
            """
              <persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence" version="2.1">
                  <persistence-unit name="orders">
                      <provider>org.hibernate.ejb.HibernatePersistence</provider>
                  </persistence-unit>
              </persistence>
              """,
            """
              <persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence" version="2.1">
                  <persistence-unit name="orders">
                      <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
                  </persistence-unit>
              </persistence>
              """,
            spec -> spec.path("src/main/resources/META-INF/persistence.xml")
          )
        );
    }

    @Test
    void replaceRemovedBootstrapTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              import org.hibernate.SessionFactory;
              import org.hibernate.cfg.AnnotationConfiguration;
              import org.hibernate.service.ServiceRegistry;
              import org.hibernate.service.ServiceRegistryBuilder;

              class Bootstrap {
                  SessionFactory build(Map<String, Object> settings) {
                      AnnotationConfiguration configuration = new AnnotationConfiguration();
                      ServiceRegistry registry = new ServiceRegistryBuilder()
                        .applySettings(settings)
                        .buildServiceRegistry();
                      return configuration.buildSessionFactory(registry);
                  }
              }
              """,
            """
              import java.util.Map;

              import org.hibernate.SessionFactory;
              import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
              import org.hibernate.cfg.Configuration;
              import org.hibernate.service.ServiceRegistry;

              class Bootstrap {
                  SessionFactory build(Map<String, Object> settings) {
                      Configuration configuration = new Configuration();
                      ServiceRegistry registry = new StandardServiceRegistryBuilder()
                        .applySettings(settings)
                        .build();
                      return configuration.buildSessionFactory(registry);
                  }
              }
              """
          )
        );
    }
}
