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

class MigrateNamedQueryToStatementTest implements RewriteTest {

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
    void dmlNamedQueryBecomesNamedStatement() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQuery;

                        @NamedQuery(name = "Book.deleteOld", query = "delete from Book where year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedStatement;

                        @NamedStatement(name = "Book.deleteOld", statement = "delete from Book where year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void selectNamedQueryIsNotChanged() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQuery;

                        @NamedQuery(name = "Book.findAll", query = "select b from Book b")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void mixedNamedQueriesContainerSplitsDmlOut() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQueries;
                        import jakarta.persistence.NamedQuery;

                        @NamedQueries({
                            @NamedQuery(name = "Book.findAll", query = "select b from Book b"),
                            @NamedQuery(name = "Book.deleteOld", query = "delete from Book where year < 2000")
                        })
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQueries;
                        import jakarta.persistence.NamedQuery;
                        import jakarta.persistence.NamedStatement;

                        @NamedQueries({
                            @NamedQuery(name = "Book.findAll", query = "select b from Book b")
                        })
                        @NamedStatement(name = "Book.deleteOld", statement = "delete from Book where year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void allDmlNamedQueriesContainerFullyReplaced() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQueries;
                        import jakarta.persistence.NamedQuery;

                        @NamedQueries({
                            @NamedQuery(name = "Book.deleteOld", query = "delete from Book where year < 2000"),
                            @NamedQuery(name = "Book.updateTitle", query = "update Book set title = :t where id = :id")
                        })
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedStatement;

                        @NamedStatement(name = "Book.deleteOld", statement = "delete from Book where year < 2000")
                        @NamedStatement(name = "Book.updateTitle", statement = "update Book set title = :t where id = :id")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void containerAndStandaloneOnSameClass() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQueries;
                        import jakarta.persistence.NamedQuery;

                        @NamedQueries({
                            @NamedQuery(name = "Book.findAll",   query = "select b from Book b"),
                            @NamedQuery(name = "Book.deleteOld", query = "delete from Book where year < 2000")
                        })
                        @NamedQuery(name = "Book.updateTitle", query = "update Book set title = :t")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQueries;
                        import jakarta.persistence.NamedQuery;
                        import jakarta.persistence.NamedStatement;

                        @NamedQueries({
                            @NamedQuery(name = "Book.findAll",   query = "select b from Book b")
                        })
                        @NamedStatement(name = "Book.deleteOld", statement = "delete from Book where year < 2000")
                        @NamedStatement(name = "Book.updateTitle", statement = "update Book set title = :t")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void allSelectNamedQueriesContainerUnchanged() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedQueries;
                        import jakarta.persistence.NamedQuery;

                        @NamedQueries({
                            @NamedQuery(name = "Book.findAll", query = "select b from Book b"),
                            @NamedQuery(name = "Book.findById", query = "select b from Book b where b.id = :id")
                        })
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }

    @Test
    void dmlNamedNativeQueryBecomesNamedNativeStatement() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeQuery;

                        @NamedNativeQuery(name = "Book.nativeDelete", query = "delete from BOOK where year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import jakarta.persistence.NamedNativeStatement;

                        @NamedNativeStatement(name = "Book.nativeDelete", statement = "delete from BOOK where year < 2000")
                        @Entity
                        class Book {
                            @Id Long id;
                        }
                        """
                )
        );
    }
}
