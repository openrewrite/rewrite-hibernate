// SPDX-License-Identifier: Apache-2.0
// Copyright Red Hat Inc. and Hibernate Authors
package org.openrewrite.hibernate.jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Collections;
import java.util.List;

import static org.openrewrite.java.Assertions.java;

class MigrateNamedNativeQueryToStatementTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .recipe(new MigrateNamedQueryToStatement())
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .styles(List.of(new NamedStyles(
                                Tree.randomId(), "custom", "", "", Collections.emptySet(),
                                List.of(IntelliJ.importLayout()
                                        .withClassCountToUseStarImport(999)
                                        .withNameCountToUseStarImport(999))
                        )))
                        .classpath("jakarta.persistence-api"));
    }

    @DocumentExample
    @Test
    void dmlNamedNativeQueryBecomesNamedNativeStatement() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeQuery;

                        @NamedNativeQuery(name = "Book.nativeDelete", query = "DELETE FROM BOOK WHERE year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeStatement;

                        @NamedNativeStatement(name = "Book.nativeDelete", statement = "DELETE FROM BOOK WHERE year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void selectNamedNativeQueryIsNotChanged() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeQuery;

                        @NamedNativeQuery(name = "Book.findAll", query = "SELECT * FROM BOOK")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void updateNamedNativeQueryBecomesNamedNativeStatement() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeQuery;

                        @NamedNativeQuery(name = "Book.nativeUpdate", query = "UPDATE BOOK SET title = :t WHERE id = :id")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeStatement;

                        @NamedNativeStatement(name = "Book.nativeUpdate", statement = "UPDATE BOOK SET title = :t WHERE id = :id")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void mixedNamedNativeQueriesContainerSplitsDmlOut() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeQueries;
                        import jakarta.persistence.NamedNativeQuery;

                        @NamedNativeQueries({
                            @NamedNativeQuery(name = "Book.findAll",   query = "SELECT * FROM BOOK"),
                            @NamedNativeQuery(name = "Book.deleteOld", query = "DELETE FROM BOOK WHERE year < 2000")
                        })
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeQueries;
                        import jakarta.persistence.NamedNativeQuery;
                        import jakarta.persistence.NamedNativeStatement;

                        @NamedNativeQueries({
                            @NamedNativeQuery(name = "Book.findAll",   query = "SELECT * FROM BOOK")
                        })
                        @NamedNativeStatement(name = "Book.deleteOld", statement = "DELETE FROM BOOK WHERE year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void allDmlNamedNativeQueriesContainerFullyReplaced() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeQueries;
                        import jakarta.persistence.NamedNativeQuery;

                        @NamedNativeQueries({
                            @NamedNativeQuery(name = "Book.nativeDelete", query = "DELETE FROM BOOK WHERE year < 2000"),
                            @NamedNativeQuery(name = "Book.nativeUpdate", query = "UPDATE BOOK SET title = :t WHERE id = :id")
                        })
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeStatement;

                        @NamedNativeStatement(name = "Book.nativeDelete", statement = "DELETE FROM BOOK WHERE year < 2000")
                        @NamedNativeStatement(name = "Book.nativeUpdate", statement = "UPDATE BOOK SET title = :t WHERE id = :id")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void allSelectNamedNativeQueriesContainerUnchanged() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeQueries;
                        import jakarta.persistence.NamedNativeQuery;

                        @NamedNativeQueries({
                            @NamedNativeQuery(name = "Book.findAll",  query = "SELECT * FROM BOOK"),
                            @NamedNativeQuery(name = "Book.findById", query = "SELECT * FROM BOOK WHERE id = :id")
                        })
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }
}
