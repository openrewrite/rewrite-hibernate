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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateHibernateEnhanceMavenPluginTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.MigrateToHibernate70");
    }

    @DocumentExample
    @Test
    void pluginSharesHibernateVersionProperty() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                  <hibernate.version>6.5.3.Final</hibernate.version>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>org.hibernate.orm</groupId>
                    <artifactId>hibernate-core</artifactId>
                    <version>${hibernate.version}</version>
                  </dependency>
                </dependencies>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>org.hibernate.orm.tooling</groupId>
                      <artifactId>hibernate-enhance-maven-plugin</artifactId>
                      <version>${hibernate.version}</version>
                      <executions>
                        <execution>
                          <id>enhance</id>
                          <goals>
                            <goal>enhance</goal>
                          </goals>
                          <configuration>
                            <enableLazyInitialization>true</enableLazyInitialization>
                            <enableDirtyTracking>true</enableDirtyTracking>
                          </configuration>
                        </execution>
                      </executions>
                    </plugin>
                  </plugins>
                </build>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("<hibernate.version>(7\\.0\\.\\d+\\.Final)</hibernate.version>").matcher(pom);
                assertThat(version.find()).describedAs("Expected hibernate.version 7.0.x in %s", pom).isTrue();
                return """
                  <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <properties>
                      <hibernate.version>%s</hibernate.version>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>org.hibernate.orm</groupId>
                        <artifactId>hibernate-core</artifactId>
                        <version>${hibernate.version}</version>
                      </dependency>
                    </dependencies>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.hibernate.orm</groupId>
                          <artifactId>hibernate-maven-plugin</artifactId>
                          <version>${hibernate.version}</version>
                          <executions>
                            <execution>
                              <id>enhance</id>
                              <goals>
                                <goal>enhance</goal>
                              </goals>
                              <configuration>
                                <enableLazyInitialization>true</enableLazyInitialization>
                                <enableDirtyTracking>true</enableDirtyTracking>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                  """.formatted(version.group(1));
            })
          )
        );
    }

    @Test
    void pluginWithLiteralVersion() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>org.hibernate.orm.tooling</groupId>
                      <artifactId>hibernate-enhance-maven-plugin</artifactId>
                      <version>6.6.5.Final</version>
                    </plugin>
                  </plugins>
                </build>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("<version>(7\\.0\\.\\d+\\.Final)</version>").matcher(pom);
                assertThat(version.find()).describedAs("Expected plugin version 7.0.x in %s", pom).isTrue();
                return """
                  <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.hibernate.orm</groupId>
                          <artifactId>hibernate-maven-plugin</artifactId>
                          <version>%s</version>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                  """.formatted(version.group(1));
            })
          )
        );
    }

    @Test
    void leaveOtherPluginsAlone() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>org.apache.maven.plugins</groupId>
                      <artifactId>maven-compiler-plugin</artifactId>
                      <version>3.13.0</version>
                    </plugin>
                  </plugins>
                </build>
              </project>
              """
          )
        );
    }
}
