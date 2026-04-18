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

class MigrateToHibernate70Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate70")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "hibernate-core-5+",
              "javax.persistence-api"));
    }

    @DocumentExample
    @Test
    void migratesMetadataContributor() {
        rewriteRun(
          java(
            """
            import org.hibernate.boot.spi.MetadataContributor;

            class MyContributor implements MetadataContributor {
            }
            """,
            """
            import org.hibernate.boot.spi.AdditionalMappingContributor;

            class MyContributor implements AdditionalMappingContributor {
            }
            """
          )
        );
    }

    @Test
    void migratesSaveToPersist() {
        rewriteRun(
          java(
            """
            import org.hibernate.Session;

            class MyService {
                void save(Session session, Object entity) {
                    session.save(entity);
                }
            }
            """,
            """
            import org.hibernate.Session;

            class MyService {
                void save(Session session, Object entity) {
                    session.persist(entity);
                }
            }
            """
          )
        );
    }

    @Test
    void migratesDeleteToRemove() {
        rewriteRun(
          java(
            """
            import org.hibernate.Session;

            class MyService {
                void delete(Session session, Object entity) {
                    session.delete(entity);
                }
            }
            """,
            """
            import org.hibernate.Session;

            class MyService {
                void delete(Session session, Object entity) {
                    session.remove(entity);
                }
            }
            """
          )
        );
    }

    @Test
    void migratesGetToFind() {
        rewriteRun(
          java(
            """
            import org.hibernate.Session;

            class MyService {
                Object load(Session session) {
                    return session.get(String.class, "id");
                }
            }
            """,
            """
            import org.hibernate.Session;

            class MyService {
                Object load(Session session) {
                    return session.find(String.class, "id");
                }
            }
            """
          )
        );
    }
}
