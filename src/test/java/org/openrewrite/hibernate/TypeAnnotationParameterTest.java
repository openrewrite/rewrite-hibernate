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
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.internal.ListUtils;
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
              import io.hypersistence.utils.hibernate.type.json.JsonType;
              import org.hibernate.annotations.Type;

              class MyEntity {
                  @Type(JsonType.class)
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
              import io.hypersistence.utils.hibernate.type.json.JsonType;
              import org.hibernate.annotations.Type;

              class MyEntity {
                  @Type(JsonType.class)
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


    @Issue("https://github.com/openrewrite/rewrite-hibernate/issues/107")
    @ParameterizedTest
    @CsvSource({
      "uuid-char, CHAR, java.util.UUID",
      "uuid-binary, BINARY, java.util.UUID",
      "text, LONGVARCHAR, String"
    })
    void basicTypeRegistryNames(String name, String jdbcType, String javaType) {
        rewriteRun(
          spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
          java(
            """
              import org.hibernate.annotations.Type;

              class Example {
                  @Type(type = "%s")
                  %s value;
              }
              """.formatted(name, javaType),
            """
              import org.hibernate.annotations.JdbcTypeCode;
              import org.hibernate.type.SqlTypes;

              class Example {
                  @JdbcTypeCode(SqlTypes.%s)
                  %s value;
              }
              """.formatted(jdbcType, javaType)
          )
        );
    }


    @ParameterizedTest
    @CsvSource({"uuid-char, CHAR, java.util.UUID", "uuid-binary, BINARY, java.util.UUID", "text, LONGVARCHAR, String"})
    void basicTypeOnGetter(String name, String jdbcType, String javaType) {
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.Type;

              class Example {
                  @Type(type = "%s")
                  %s getValue() {
                      return null;
                  }
              }
              """.formatted(name, javaType),
            """
              import org.hibernate.annotations.JdbcTypeCode;
              import org.hibernate.type.SqlTypes;

              class Example {
                  @JdbcTypeCode(SqlTypes.%s)
                  %s getValue() {
                      return null;
                  }
              }
              """.formatted(jdbcType, javaType)
          )
        );
    }

    @ParameterizedTest
    @CsvSource({"uuid-char, String", "uuid-char, java.util.UUID[]", "uuid-binary, java.util.List<java.util.UUID>", "text, char[]"})
    void incompatibleAndContainerTypesArePreserved(String name, String javaType) {
        rewriteRun(java("""
          import org.hibernate.annotations.Type;

          class Example {
              @Type(type = "%s")
              %s value;
          }
          """.formatted(name, javaType)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jsonb", "custom-name", "yes_no", "true_false", "numeric_boolean"})
    void unknownRegistryNamesArePreserved(String name) {
        rewriteRun(java("""
          import org.hibernate.annotations.Type;

          class Example {
              @Type(type = "%s")
              Object value;
          }
          """.formatted(name)));
    }

    @Test
    void basicTypeParametersArePreserved() {
        rewriteRun(java("""
          import org.hibernate.annotations.Parameter;
          import org.hibernate.annotations.Type;

          class Example {
              @Type(type = "uuid-char", parameters = @Parameter(name = "custom", value = "true"))
              java.util.UUID value;
          }
          """));
    }

    @Test
    void emptyBasicTypeParameters() {
        rewriteRun(java("""
          import org.hibernate.annotations.Type;

          class Example {
              @Type(type = "uuid-char", parameters = {})
              java.util.UUID value;
          }
          """, """
          import org.hibernate.annotations.JdbcTypeCode;
          import org.hibernate.type.SqlTypes;

          class Example {
              @JdbcTypeCode(SqlTypes.CHAR)
              java.util.UUID value;
          }
          """));
    }

    @Test
    void explicitJdbcMappingIsPreserved() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "hibernate-core-5+", "hibernate-core-6+")),
          java("""
            import org.hibernate.annotations.JdbcTypeCode;
            import org.hibernate.annotations.Type;

            class Example {
                @JdbcTypeCode(java.sql.Types.VARCHAR)
                @Type(type = "uuid-char")
                java.util.UUID value;
            }
            """)
        );
    }

    @Test
    void importedUserTypeName() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-5+")
            .dependsOn("package com.example; public abstract class CustomType implements org.hibernate.usertype.UserType {}")),
          java("""
            import com.example.CustomType;
            import org.hibernate.annotations.Type;

            class Example {
                @Type(type = "CustomType", parameters = {})
                Object value;
            }
            """, """
            import com.example.CustomType;
            import org.hibernate.annotations.Type;

            class Example {
                @Type(value = CustomType.class, parameters = {})
                Object value;
            }
            """)
        );
    }

    @Test
    void localAliasShadowsBasicRegistryName() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-5+")
            .dependsOn("package com.example; public abstract class CustomType implements org.hibernate.usertype.UserType {}")),
          java("""
            import com.example.CustomType;
            import org.hibernate.annotations.Type;
            import org.hibernate.annotations.TypeDef;

            @TypeDef(name = "uuid-char", typeClass = CustomType.class)
            class Example {
                @Type(type = "uuid-char", parameters = {})
                java.util.UUID value;
            }
            """, """
            import com.example.CustomType;
            import org.hibernate.annotations.Type;

            class Example {
                @Type(value = CustomType.class, parameters = {})
                java.util.UUID value;
            }
            """)
        );
    }

    @Test
    void composesWithBooleanMappings() {
        rewriteRun(
          spec -> spec.recipes(new MigrateBooleanMappings(), new TypeAnnotationParameter()),
          java("""
            import org.hibernate.annotations.Type;

            class Example {
                @Type(type = "yes_no")
                boolean enabled;
                @Type(type = "uuid-char")
                java.util.UUID id;
            }
            """, """
            import jakarta.persistence.Convert;
            import org.hibernate.annotations.JdbcTypeCode;
            import org.hibernate.type.SqlTypes;
            import org.hibernate.type.YesNoConverter;

            class Example {
                @Convert(converter = YesNoConverter.class)
                boolean enabled;
                @JdbcTypeCode(SqlTypes.CHAR)
                java.util.UUID id;
            }
            """)
        );
    }


    @Test
    void defaultPackageUserTypeClass() {
        rewriteRun(java("""
          import org.hibernate.annotations.Type;
          import org.hibernate.usertype.UserType;

          class Example {
              @Type(type = "CustomType")
              Object value;
          }

          abstract class CustomType implements UserType {
          }
          """, """
          import org.hibernate.annotations.Type;
          import org.hibernate.usertype.UserType;

          class Example {
              @Type(CustomType.class)
              Object value;
          }

          abstract class CustomType implements UserType {
          }
          """));
    }

    @Test
    void aliasWithPartiallyAttributedClassLiteral() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-5+")
            .dependsOn("package com.example; public abstract class CustomType implements org.hibernate.usertype.UserType {}")),
          java("""
            import org.hibernate.annotations.Type;
            import org.hibernate.annotations.TypeDef;

            @TypeDef(name = "uuid-char", typeClass = com.example.CustomType.class)
            class Example {
                @Type(type = "uuid-char", parameters = {})
                Object value;
            }
            """, """
            import com.example.CustomType;
            import org.hibernate.annotations.Type;

            class Example {
                @Type(value = CustomType.class, parameters = {})
                Object value;
            }
            """, spec -> spec.mapBeforeRecipe(cu -> (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                    J.Annotation a = super.visitAnnotation(annotation, ctx);
                    if (!"TypeDef".equals(a.getSimpleName())) {
                        return a;
                    }
                    // Model a missing Class-expression type while retaining its valid, attributed class target.
                    // Type validation remains enabled; this is an explicit partial-LST boundary.
                    return a.withArguments(ListUtils.map(a.getArguments(), argument -> {
                        if (argument instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) argument;
                            if ("typeClass".equals(assignment.getVariable().toString()) && assignment.getAssignment() instanceof J.FieldAccess) {
                                return assignment.withAssignment(((J.FieldAccess) assignment.getAssignment()).withType(JavaType.Unknown.getInstance()));
                            }
                        }
                        return argument;
                    }));
                }
            }.visitNonNull(cu, new InMemoryExecutionContext())))
        );
    }

    @Test
    void userTypeParametersAreNotTemporalTypeNames() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-5+")
            .dependsOn("package com.example; public abstract class CustomType implements org.hibernate.usertype.UserType {}")),
          java("""
            import org.hibernate.annotations.Parameter;
            import org.hibernate.annotations.Type;

            class Example {
                @Type(type = "com.example.CustomType", parameters = @Parameter(name = "format", value = "timestamp"))
                Object value;
            }
            """, """
            import com.example.CustomType;
            import org.hibernate.annotations.Parameter;
            import org.hibernate.annotations.Type;

            class Example {
                @Type(value = CustomType.class, parameters = @Parameter(name = "format", value = "timestamp"))
                Object value;
            }
            """)
        );
    }


    @ParameterizedTest
    @ValueSource(strings = {"java.util.UUID[] getValue()", "java.util.UUID getValue()[]", "java.util.List<java.util.UUID> getValue()"})
    void arrayAndContainerGettersArePreserved(String signature) {
        rewriteRun(java("""
          import org.hibernate.annotations.Type;

          class Example {
              @Type(type = "uuid-char")
              %s {
                  return null;
              }
          }
          """.formatted(signature)));
    }

}
