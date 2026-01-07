/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TypeAnnotationParameterTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new TypeAnnotationParameter())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-5+"));
    }

    @DocumentExample
    @Test
    void onlyOneParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;

              class TestApplication {
                  @Type(type = "java.util.concurrent.atomic.AtomicBoolean")
                  Object a;
              }
              """,
            """
              import org.hibernate.annotations.Type;

              class TestApplication {
                  @Type(java.util.concurrent.atomic.AtomicBoolean.class)
                  Object a;
              }
              """
          )
        );
    }

    @Test
    void multipleParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;

              class TestApplication {
                  @Type(type = "java.util.concurrent.atomic.AtomicBoolean", parameters = {})
                  Object a;
              }
              """,
            """
              import org.hibernate.annotations.Type;

              class TestApplication {
                  @Type(value = java.util.concurrent.atomic.AtomicBoolean.class, parameters = {})
                  Object a;
              }
              """
          )
        );
    }

    @Test
    void removedParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;

              class TestApplication {
                  @Type(type = "org.hibernate.type.TextType")
                  Object a;
              }
              """,
            """
              class TestApplication {
                 \s
                  Object a;
              }
              """
          )
        );
    }

    @Test
    void temporalTypesConvertedSeparately() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;

              import java.util.Date;

              class TestApplication {
                  @Type(type = "timestamp")
                  Date a;
              }
              """,
            """
              import jakarta.persistence.Temporal;
              import jakarta.persistence.TemporalType;

              import java.util.Date;

              class TestApplication {
                  @Temporal(TemporalType.TIMESTAMP)
                  Date a;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-hibernate/issues/55")
    @Test
    void adoptTypeDefClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;
              import org.hibernate.annotations.TypeDef;

              @TypeDef(name = "stringy", typeClass = String.class)
              class TestApplication {
                  @Type(type = "stringy")
                  Object a;
              }
              """,
            """
              import org.hibernate.annotations.Type;


              class TestApplication {
                  @Type(String.class)
                  Object a;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-hibernate/issues/55")
    @Test
    void adoptTypeDefClassWithParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;
              import org.hibernate.annotations.TypeDef;

              @TypeDef(name = "stringy", typeClass = String.class)
              class TestApplication {
                  @Type(type = "stringy", parameters = {})
                  Object a;
              }
              """,
            """
              import org.hibernate.annotations.Type;


              class TestApplication {
                  @Type(value = String.class, parameters = {})
                  Object a;
              }
              """
          )
        );
    }

    @Test
    void qualifiedTypeClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.TypeDef;
              import org.hibernate.annotations.Type;

              @TypeDef(name = "json", typeClass = io.hypersistence.utils.hibernate.type.json.JsonType.class)
              class MyEntity {
                  @Type(type = "json")
                  private String data;
              }
              """,
            //language=java
            """
              import org.hibernate.annotations.Type;


              class MyEntity {
                  @Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
                  private String data;
              }
              """
          )
        );
    }

    @Test
    void importedTypeClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.Type;
              import org.hibernate.annotations.TypeDef;
              import java.util.concurrent.atomic.AtomicBoolean;

              @TypeDef(name = "bool", typeClass = AtomicBoolean.class)
              class TestApplication {
                  @Type(type = "bool")
                  Object a;
              }
              """,
            """
              import org.hibernate.annotations.Type;
              import java.util.concurrent.atomic.AtomicBoolean;


              class TestApplication {
                  @Type(AtomicBoolean.class)
                  Object a;
              }
              """
          )
        );
    }

    @Test
    void qualifiedTypeDefsOnClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.TypeDefs;
              import org.hibernate.annotations.TypeDef;
              import org.hibernate.annotations.Type;

              @TypeDefs(@TypeDef(name = "json", typeClass = io.hypersistence.utils.hibernate.type.json.JsonType.class))
              class MyEntity {
                  @Type(type = "json")
                  private String data;
              }

              class JsonType {}
              """,
            //language=java
            """
              import org.hibernate.annotations.Type;


              class MyEntity {
                  @Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
                  private String data;
              }

              class JsonType {}
              """
          )
        );
    }

    @Test
    void importedTypeDefsOnClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.annotations.TypeDefs;
              import org.hibernate.annotations.TypeDef;
              import org.hibernate.annotations.Type;
              import io.hypersistence.utils.hibernate.type.json.JsonType;

              @TypeDefs(@TypeDef(name = "json", typeClass = JsonType.class))
              class MyEntity {
                  @Type(type = "json")
                  private String data;
              }

              class JsonType {}
              """,
            //language=java
            """
              import org.hibernate.annotations.Type;
              import io.hypersistence.utils.hibernate.type.json.JsonType;


              class MyEntity {
                  @Type(JsonType.class)
                  private String data;
              }

              class JsonType {}
              """
          )
        );
    }

}
