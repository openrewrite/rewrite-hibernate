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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateToHibernate56JakartaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate56Jakarta")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "javax.persistence-api"));
    }

    @DocumentExample
    @Test
    void switchToJakartaArtifacts() {
        rewriteRun(
          mavenProject("a",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>a</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.hibernate</groupId>
                      <artifactId>hibernate-core</artifactId>
                      <version>5.4.33.Final</version>
                    </dependency>
                    <dependency>
                      <groupId>org.hibernate</groupId>
                      <artifactId>hibernate-envers</artifactId>
                      <version>5.4.33.Final</version>
                    </dependency>
                    <dependency>
                      <groupId>javax.persistence</groupId>
                      <artifactId>javax.persistence-api</artifactId>
                      <version>2.2</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> {
                  Matcher matcher = Pattern.compile("<version>(3\\.0\\.\\d+)</version>").matcher(actual);
                  assertThat(matcher.find()).isTrue();
                  return """
                    <project>
                      <groupId>org.example</groupId>
                      <artifactId>a</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                        <dependency>
                          <groupId>org.hibernate</groupId>
                          <artifactId>hibernate-core-jakarta</artifactId>
                          <version>5.6.15.Final</version>
                        </dependency>
                        <dependency>
                          <groupId>org.hibernate</groupId>
                          <artifactId>hibernate-envers-jakarta</artifactId>
                          <version>5.6.15.Final</version>
                        </dependency>
                        <dependency>
                          <groupId>jakarta.persistence</groupId>
                          <artifactId>jakarta.persistence-api</artifactId>
                          <version>%s</version>
                        </dependency>
                      </dependencies>
                    </project>
                    """.formatted(matcher.group(1));
              })
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import javax.persistence.Entity;
                  import javax.persistence.Id;

                  @Entity
                  class Order {
                      @Id
                      Long id;
                  }
                  """,
                """
                  import jakarta.persistence.Entity;
                  import jakarta.persistence.Id;

                  @Entity
                  class Order {
                      @Id
                      Long id;
                  }
                  """
              )
            )
          )
        );
    }
}
