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

              public class TestApplication {
                  @Type(type = "java.util.concurrent.atomic.AtomicBoolean")
                  Object a;
              }
              """,
            """
              import org.hibernate.annotations.Type;

              import java.util.concurrent.atomic.AtomicBoolean;

              public class TestApplication {
                  @Type(AtomicBoolean.class)
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

              import java.util.concurrent.atomic.AtomicBoolean;

              class TestApplication {
                  @Type(value = AtomicBoolean.class, parameters = {})
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

              public class TestApplication {
                  @Type(type = "org.hibernate.type.TextType")
                  Object a;
              }
              """,
            """
              public class TestApplication {
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
              import java.util.Date;
              import org.hibernate.annotations.Type;

              public class TestApplication {
                  @Type(type = "timestamp")
                  Date a;
              }
              """,
            """
              import java.util.Date;

              import jakarta.persistence.Temporal;
              import jakarta.persistence.TemporalType;

              public class TestApplication {
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
              public class TestApplication {
                  @Type(type = "stringy")
                  Object a;
              }
              """,
            """
              import org.hibernate.annotations.Type;


              public class TestApplication {
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
              public class TestApplication {
                  @Type(type = "stringy", parameters = {})
                  Object a;
              }
              """,
            """
              import org.hibernate.annotations.Type;


              public class TestApplication {
                  @Type(value = String.class, parameters = {})
                  Object a;
              }
              """
          )
        );
    }
}
