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

class RemoveGeneratedValueStrategyWithGenericGeneratorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveGeneratedValueStrategyWithGenericGenerator())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-6+", "jakarta.persistence-api"));
    }

    @DocumentExample
    @Test
    void dropStrategyTableWhenGenericGeneratorPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;
              import org.hibernate.annotations.Parameter;

              @Entity
              class A {
                  @Id
                  @GenericGenerator(name = "MY_ID_GEN", strategy = "com.example.MyTableGenerator",
                      parameters = { @Parameter(name = "table", value = "MY_ID_SEQ") })
                  @GeneratedValue(strategy = GenerationType.TABLE, generator = "MY_ID_GEN")
                  Long id;
              }
              """,
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;
              import org.hibernate.annotations.Parameter;

              @Entity
              class A {
                  @Id
                  @GenericGenerator(name = "MY_ID_GEN", strategy = "com.example.MyTableGenerator",
                      parameters = { @Parameter(name = "table", value = "MY_ID_SEQ") })
                  @GeneratedValue(generator = "MY_ID_GEN")
                  Long id;
              }
              """
          )
        );
    }

    @Test
    void dropStrategySequenceWhenGenericGeneratorPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              class A {
                  @Id
                  @GenericGenerator(name = "MY_ID_GEN", strategy = "com.example.MySequenceGenerator")
                  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MY_ID_GEN")
                  Long id;
              }
              """,
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              class A {
                  @Id
                  @GenericGenerator(name = "MY_ID_GEN", strategy = "com.example.MySequenceGenerator")
                  @GeneratedValue(generator = "MY_ID_GEN")
                  Long id;
              }
              """
          )
        );
    }

    @Test
    void leaveAloneWhenTableGeneratorMatchesName() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;
              import jakarta.persistence.TableGenerator;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              @TableGenerator(name = "GEN", table = "GEN_TABLE")
              class A {
                  @Id
                  @GenericGenerator(name = "OTHER", strategy = "org.hibernate.id.UUIDGenerator")
                  @GeneratedValue(strategy = GenerationType.TABLE, generator = "GEN")
                  Long id;
              }
              """
          )
        );
    }

    @Test
    void leaveAloneWhenSequenceGeneratorMatchesName() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;
              import jakarta.persistence.SequenceGenerator;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              @SequenceGenerator(name = "GEN", sequenceName = "GEN_SEQ")
              class A {
                  @Id
                  @GenericGenerator(name = "OTHER", strategy = "org.hibernate.id.UUIDGenerator")
                  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "GEN")
                  Long id;
              }
              """
          )
        );
    }

    @Test
    void leaveAloneWhenNoGeneratorAttribute() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              class A {
                  @Id
                  @GenericGenerator(name = "MY_ID_GEN", strategy = "com.foo.CustomGen")
                  @GeneratedValue(strategy = GenerationType.TABLE)
                  Long id;
              }
              """
          )
        );
    }

    @Test
    void leaveAloneWhenStrategyIsIdentityOrAuto() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              class A {
                  @Id
                  @GenericGenerator(name = "MY_ID_GEN", strategy = "com.foo.CustomGen")
                  @GeneratedValue(strategy = GenerationType.AUTO, generator = "MY_ID_GEN")
                  Long id;
              }
              """
          )
        );
    }

    @Test
    void leaveAloneWhenNoGenericGeneratorPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;

              @Entity
              class A {
                  @Id
                  @GeneratedValue(strategy = GenerationType.TABLE, generator = "SOMEWHERE_ELSE")
                  Long id;
              }
              """
          )
        );
    }

    @Test
    void worksWhenGenericGeneratorIsOnSiblingField() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              class A {
                  @GenericGenerator(name = "GEN", strategy = "com.foo.CustomGen")
                  String marker;

                  @Id
                  @GeneratedValue(strategy = GenerationType.TABLE, generator = "GEN")
                  Long id;
              }
              """,
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              class A {
                  @GenericGenerator(name = "GEN", strategy = "com.foo.CustomGen")
                  String marker;

                  @Id
                  @GeneratedValue(generator = "GEN")
                  Long id;
              }
              """
          )
        );
    }

    @Test
    void preservesOtherGeneratedValueAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.GenerationType;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              class A {
                  @Id
                  @GenericGenerator(name = "GEN", strategy = "com.foo.CustomGen")
                  @GeneratedValue(generator = "GEN", strategy = GenerationType.TABLE)
                  Long id;
              }
              """,
            """
              import jakarta.persistence.Entity;
              import jakarta.persistence.GeneratedValue;
              import jakarta.persistence.Id;
              import org.hibernate.annotations.GenericGenerator;

              @Entity
              class A {
                  @Id
                  @GenericGenerator(name = "GEN", strategy = "com.foo.CustomGen")
                  @GeneratedValue(generator = "GEN")
                  Long id;
              }
              """
          )
        );
    }
}
