// SPDX-License-Identifier: Apache-2.0
// Copyright Red Hat Inc. and Hibernate Authors
package org.openrewrite.hibernate.jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateEntityManagerGetDelegateTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .recipe(new MigrateEntityManagerGetDelegate())
                .parser(org.openrewrite.java.JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpath("jakarta.persistence-api"));
    }

    @DocumentExample
    @Test
    void getdelegateInAssignmentReplacedWithUnwrap() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.EntityManager;
                        
                        class MyService {
                            void method(EntityManager em) {
                                Object delegate = em.getDelegate();
                            }
                        }
                        """,
                        """
                        import jakarta.persistence.EntityManager;
                        
                        class MyService {
                            void method(EntityManager em) {
                                Object delegate = em.unwrap(Object.class);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void getdelegateWithCastReplacedWithUnwrap() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.EntityManager;
                        
                        class MyService {
                            void method(EntityManager em) {
                                Object delegate = (Object) em.getDelegate();
                            }
                        }
                        """,
                        """
                        import jakarta.persistence.EntityManager;
                        
                        class MyService {
                            void method(EntityManager em) {
                                Object delegate = (Object) em.unwrap(Object.class);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void getdelegateInReturnStatementReplacedWithUnwrap() {
        rewriteRun(
                //language=java
                java(
                        """
                        import jakarta.persistence.EntityManager;
                        
                        class MyService {
                            Object getUnderlying(EntityManager em) {
                                return em.getDelegate();
                            }
                        }
                        """,
                        """
                        import jakarta.persistence.EntityManager;
                        
                        class MyService {
                            Object getUnderlying(EntityManager em) {
                                return em.unwrap(Object.class);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void getDelegateOnNonEntityManagerIsNotChanged() {
        rewriteRun(
                //language=java
                java(
                        """
                        class OtherManager {
                            Object getDelegate() { return this; }
                        
                            void method(OtherManager other) {
                                Object d = other.getDelegate();
                            }
                        }
                        """
                )
        );
    }
}
