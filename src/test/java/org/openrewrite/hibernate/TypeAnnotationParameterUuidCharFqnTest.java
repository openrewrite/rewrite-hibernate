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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-hibernate/issues/31")
class TypeAnnotationParameterUuidCharFqnTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TypeAnnotationParameter())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-5+"))
          .cycles(3)
          .expectedCyclesThatMakeChanges(0);
    }

    @Test
    void migrateBuiltInUuidCharFqnOnScalarUuidField() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;

              class Example {
                  @Type(type = "org.hibernate.type.UUIDCharType")
                  java.util.UUID id;
              }
              """,
            """
              import org.hibernate.annotations.JdbcTypeCode;
              import org.hibernate.type.SqlTypes;

              class Example {
                  @JdbcTypeCode(SqlTypes.VARCHAR)
                  java.util.UUID id;
              }
              """
          )
        );
    }

    @Test
    void migrateBuiltInUuidCharFqnOnScalarUuidGetter() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;

              class Example {
                  @Type(type = "org.hibernate.type.UUIDCharType")
                  java.util.UUID getId() {
                      return null;
                  }
              }
              """,
            """
              import org.hibernate.annotations.JdbcTypeCode;
              import org.hibernate.type.SqlTypes;

              class Example {
                  @JdbcTypeCode(SqlTypes.VARCHAR)
                  java.util.UUID getId() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainBuiltInFqnWithNonemptyParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Parameter;
              import org.hibernate.annotations.Type;

              class Example {
                  @Type(type = "org.hibernate.type.UUIDCharType", parameters = @Parameter(name = "custom", value = "true"))
                  java.util.UUID id;
              }
              """
          )
        );
    }

    @Test
    void retainBuiltInFqnWithExistingJdbcTypeCode() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(), "hibernate-core-5+", "hibernate-core-6+")),
          //language=java
          java(
            """
              import org.hibernate.annotations.JdbcTypeCode;
              import org.hibernate.annotations.Type;

              class Example {
                  @JdbcTypeCode(java.sql.Types.VARCHAR)
                  @Type(type = "org.hibernate.type.UUIDCharType")
                  java.util.UUID id;
              }
              """
          )
        );
    }

    @Test
    void retainBuiltInFqnOnUnsupportedOwners() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;

              class Example {
                  @Type(type = "org.hibernate.type.UUIDCharType")
                  java.util.List<java.util.UUID> ids;

                  @Type(type = "org.hibernate.type.UUIDCharType")
                  java.util.UUID[] getIds() {
                      return null;
                  }

                  @Type(type = "org.hibernate.type.UUIDCharType")
                  java.util.UUID findId(java.util.UUID id) {
                      return id;
                  }
              }
              """
          )
        );
    }

    @Test
    void localTypeDefStillOverridesTheBuiltInFqn() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1)
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-5+")
              .dependsOn("package com.example; public abstract class CustomType implements org.hibernate.usertype.UserType {}")),
          //language=java
          java(
            """
              import com.example.CustomType;
              import org.hibernate.annotations.Type;
              import org.hibernate.annotations.TypeDef;

              @TypeDef(name = "org.hibernate.type.UUIDCharType", typeClass = CustomType.class)
              class Example {
                  @Type(type = "org.hibernate.type.UUIDCharType", parameters = {})
                  java.util.UUID id;
              }
              """,
            """
              import com.example.CustomType;
              import org.hibernate.annotations.Type;

              class Example {
                  @Type(value = CustomType.class, parameters = {})
                  java.util.UUID id;
              }
              """
          )
        );
    }
}
