/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateToHibernate60Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.hibernate")
          .build()
          .activateRecipes("org.openrewrite.hibernate.MigrateToHibernate60")
        );
    }

    @DocumentExample
    @Test
    void removesEntityManager() {
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
                      <artifactId>hibernate-entitymanager</artifactId>
                      <version>5.6.15.Final</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>a</artifactId>
                  <version>1.0.0</version>
                </project>
                """
            )
          ),
          mavenProject("b",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>b</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.hibernate</groupId>
                        <artifactId>hibernate-entitymanager</artifactId>
                        <version>5.6.15.Final</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """,
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>b</artifactId>
                  <version>1.0.0</version>
                </project>
                """
            )
          )
        );
    }
}
