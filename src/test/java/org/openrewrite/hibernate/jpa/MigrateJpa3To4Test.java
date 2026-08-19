// SPDX-License-Identifier: Apache-2.0
// Copyright Red Hat Inc. and Hibernate Authors
package org.openrewrite.hibernate.jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class MigrateJpa3To4Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .recipeFromResources("org.openrewrite.hibernate.jpa.MigrateJpa3To4")
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpath("jakarta.persistence-api"));
    }

    @Test
    void allThreeMigrationsAppliedTogether() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.EntityManager;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQuery;

                        @NamedQuery(name = "Book.deleteOld", query = "delete from Book where year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;

                            void clean(EntityManager em) {
                                Object delegate = em.getDelegate();
                            }
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.EntityManager;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedStatement;

                        @NamedStatement(name = "Book.deleteOld", statement = "delete from Book where year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;

                            void clean(EntityManager em) {
                                Object delegate = em.unwrap(Object.class);
                            }
                        }
                        """
                ),
                //language=xml
                xml(
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="3.2"
                                     xmlns="https://jakarta.ee/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_2.xsd">
                            <persistence-unit name="my-unit">
                            </persistence-unit>
                        </persistence>
                        """,
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="4.0"
                                     xmlns="https://jakarta.ee/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_4_0.xsd">
                            <persistence-unit name="my-unit">
                            </persistence-unit>
                        </persistence>
                        """
                )
        );
    }
}
