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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateResultCheckStyleToExpectationTest implements RewriteTest {

    static Stream<String[]> generateTestParameters() {
        return Stream.of("SQLInsert", "SQLUpdate", "SQLDelete", "SQLDeleteAll").map(annotation -> new String[][]{
          new String[]{annotation, "ResultCheckStyle.NONE", "Expectation.None.class"},
          new String[]{annotation, "ResultCheckStyle.COUNT", "Expectation.RowCount.class"},
          new String[]{annotation, "ResultCheckStyle.PARAM", "Expectation.OutParameter.class"}
        }).flatMap(Stream::of);
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateResultCheckStyleToExpectation())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-6.5+")
          );
    }

    @DocumentExample
    @ParameterizedTest
    @MethodSource("generateTestParameters")
    void shouldMigrateResultCheckStyleToExpectationNone(String annotation, String oldResultCheckStyleValue, String newExpectationValue) {
        // language=java
        rewriteRun(
          java(
              """
              import org.hibernate.annotations.%1$s;
              import org.hibernate.annotations.ResultCheckStyle;

              @%1$s(check = %2$s, sql = "")
              class A {}
              """.formatted(annotation, oldResultCheckStyleValue),
              """
              import org.hibernate.annotations.%1$s;
              import org.hibernate.jdbc.Expectation;

              @%1$s(verify = %2$s, sql = "")
              class A {}
              """.formatted(annotation, newExpectationValue)
          ));
    }
}
