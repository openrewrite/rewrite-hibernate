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
package org.openrewrite.hibernate.validator;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class HibernateValidator91Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.validator.HibernateValidator_9_1")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "jakarta.validation-api"));
    }

    @DocumentExample
    @Test
    void upgradeDependencyAndMoveValidInward() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>org.hibernate</groupId>
                    <artifactId>hibernate-validator</artifactId>
                    <version>7.0.5.Final</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec.after(actual ->
              assertThat(actual)
                .contains("<groupId>org.hibernate.validator</groupId>")
                .containsPattern("<version>9\\.1\\.\\d+\\.Final</version>")
                .actual())
          ),
          //language=java
          java(
            """
              import jakarta.validation.Valid;

              import java.util.List;

              class Customer {
                  @Valid
                  private List<Order> orders;
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;

              import java.util.List;

              class Customer {
                  private List<@Valid Order> orders;
              }

              class Order {
              }
              """
          )
        );
    }
}
