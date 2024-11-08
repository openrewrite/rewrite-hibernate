/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

class RemoveInvalidHibernateGeneratedValueAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveInvalidHibernateGeneratedValueAnnotation())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "jakarta.persistence")
          );
    }

    @DocumentExample
    @Test
    void removeIncorrectlyPlacedGenerateValue() {
        //language=java
        rewriteRun(
          java(
          """
            import jakarta.persistence.Entity;
            import jakarta.persistence.GeneratedValue;
            import jakarta.persistence.Id;

            class A {
                @Id
                Integer id;
                @GeneratedValue
                String name;
            }
            """,
            """
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;

            class A {
                @Id
                Integer id;
                String name;
            }
            """
          ));
    }

    @Test
    void shouldNotRemoveCorrectlyPlacedGenerateValue() {
        //language=java
        rewriteRun(
          java(
          """
            import jakarta.persistence.Entity;
            import jakarta.persistence.GeneratedValue;
            import jakarta.persistence.Id;

            class A {
                @Id
                @GeneratedValue
                Integer id;
            }
            """
          ));
    }

    @Test
    void shouldOnlyRemoveIncorreclyPlacedGenerateValueButPreserveOthers() {
        //language=java
        rewriteRun(
          java(
          """
            import jakarta.persistence.Entity;
            import jakarta.persistence.GeneratedValue;
            import jakarta.persistence.Id;

            class A {
                @Id
                @GeneratedValue
                Integer id;
                @GeneratedValue
                String name;
            }
            """,
            """
            import jakarta.persistence.Entity;
            import jakarta.persistence.GeneratedValue;
            import jakarta.persistence.Id;

            class A {
                @Id
                @GeneratedValue
                Integer id;
                String name;
            }
            """
          ));
    }
}
