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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateBooleanMappingsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateBooleanMappings())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-6+", "jakarta.persistence-api")
          );
    }

    @DocumentExample
    @Test
    void allMappings_shouldBeReplaced() {
        //language=java
        rewriteRun(
          java(
            """
              import jakarta.persistence.Column;
              import org.hibernate.annotations.Type;
              
              public class SomeClass {
              
                  @Column(name = "IS_SOMETHING")
                  @Type(type = "true_false")
                  private boolean isSomething;
              
                  @Column(name = "IS_SOMETHING_ELSE")
                  @Type(type = "org.hibernate.type.YesNoBooleanType")
                  private boolean isSomethingElse;
              
              }
              """,
            """
              import jakarta.persistence.Column;
              import jakarta.persistence.Convert;
              import org.hibernate.type.TrueFalseConverter;
              import org.hibernate.type.YesNoConverter;
              
              public class SomeClass {
              
                  @Column(name = "IS_SOMETHING")
                  @Convert(converter = TrueFalseConverter.class)
                  private boolean isSomething;
              
                  @Column(name = "IS_SOMETHING_ELSE")
                  @Convert(converter = YesNoConverter.class)
                  private boolean isSomethingElse;
              
              }
              """
          )
        );
    }

    @CsvSource(textBlock = """
      numeric_boolean                         , NumericBooleanConverter
      true_false                              , TrueFalseConverter
      yes_no                                  , YesNoConverter
      org.hibernate.type.YesNoBooleanType     , YesNoConverter
      org.hibernate.type.TrueFalseBooleanType , TrueFalseConverter
      org.hibernate.type.NumericBooleanType   , NumericBooleanConverter
      """)
    @ParameterizedTest
    void mapping_shouldBeReplaced_whenMethodIsAnnotated(String usertype, String converter) {
        //language=java
        rewriteRun(
          java(
            """
              import jakarta.persistence.Column;
              import org.hibernate.annotations.Type;
              
              public class SomeClass {
              
                  private boolean isSomething;
              
                  @Column(name = "IS_SOMETHING")
                  @Type(type = "%s")
                  public boolean isSomething() {
                      return isSomething;
                  }
              }
              """.formatted(usertype),
            """
              import jakarta.persistence.Column;
              import jakarta.persistence.Convert;
              import org.hibernate.type.%1$s;
              
              public class SomeClass {
              
                  private boolean isSomething;
              
                  @Column(name = "IS_SOMETHING")
                  @Convert(converter = %1$s.class)
                  public boolean isSomething() {
                      return isSomething;
                  }
              }
              """.formatted(converter)
          )
        );
    }

    @CsvSource(textBlock = """
      numeric_boolean                         , NumericBooleanConverter
      true_false                              , TrueFalseConverter
      yes_no                                  , YesNoConverter
      org.hibernate.type.YesNoBooleanType     , YesNoConverter
      org.hibernate.type.TrueFalseBooleanType , TrueFalseConverter
      org.hibernate.type.NumericBooleanType   , NumericBooleanConverter
      """)
    @ParameterizedTest
    void trueFalseMapping_shouldBeReplaced_whenFieldIsAnnotated(String usertype, String converter) {
        //language=java
        rewriteRun(
          java(
            """
              import jakarta.persistence.Column;
              import org.hibernate.annotations.Type;
              
              public class SomeClass {
              
                  @Column(name = "IS_SOMETHING")
                  @Type(type = "%s")
                  private boolean isSomething;
              
                  public boolean isSomething() {
                      return isSomething;
                  }
              }
              """.formatted(usertype),
            """
              import jakarta.persistence.Column;
              import jakarta.persistence.Convert;
              import org.hibernate.type.%1$s;
              
              public class SomeClass {
              
                  @Column(name = "IS_SOMETHING")
                  @Convert(converter = %1$s.class)
                  private boolean isSomething;
              
                  public boolean isSomething() {
                      return isSomething;
                  }
              }
              """.formatted(converter)
          )
        );
    }

    @Test
    void typeImport_shouldNotBeRemoved_ifUsedElsewhere() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.Type;
              import jakarta.persistence.Column;
              
              public class SomeClass {
              
                  private boolean isSomething;
                  private Object someObject;
              
                  @Column(name = "IS_SOMETHING")
                  @Type(type = "true_false")
                  public boolean isSomething() {
                      return isSomething;
                  }
              
                  @Column(name = "SOME_OBJECT")
                  @Type(type = Object.class)
                  public Object getSomeObject() {
                      return someObject;
                  }
              }
              """,
            """
              import jakarta.persistence.Convert;
              import org.hibernate.annotations.Type;
              import org.hibernate.type.TrueFalseConverter;
              import jakarta.persistence.Column;
              
              public class SomeClass {
              
                  private boolean isSomething;
                  private Object someObject;
              
                  @Column(name = "IS_SOMETHING")
                  @Convert(converter = TrueFalseConverter.class)
                  public boolean isSomething() {
                      return isSomething;
                  }
              
                  @Column(name = "SOME_OBJECT")
                  @Type(type = Object.class)
                  public Object getSomeObject() {
                      return someObject;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChange_shouldBeMade_whenTypeIsClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.Type;
              import jakarta.persistence.Column;
              
              public class SomeClass {
              
                  private Object someObject;
              
                  @Column(name = "SOME_OBJECT")
                  @Type(type = Object.class) // we just need some class, it is not checked
                  public Object getSomeObject() {
                      return someObject;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChange_shouldBeMade_whenTypeIsNameOfClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.Type;
              import jakarta.persistence.Column;
              
              public class SomeClass {
              
                  private Object someObject;
              
                  @Column(name = "SOME_OBJECT")
                  @Type(type = "java.lang.Object") // we just need some class name, it is not checked
                  public Object getSomeObject() {
                      return someObject;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChange_shouldBeMade_whenTypeIsBoolean() {
        //language=java
        rewriteRun(
          java(
            """
              import jakarta.persistence.Column;
              import org.hibernate.annotations.Type;
              
              public class SomeClass {
              
                  @Column(name = "IS_SOMETHING")
                  @Type(type = "boolean")
                  private boolean isSomething;
              }
              """
          )
        );
    }

}
