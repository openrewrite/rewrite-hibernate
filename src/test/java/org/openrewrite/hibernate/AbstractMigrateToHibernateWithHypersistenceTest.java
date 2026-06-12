/*
 * Copyright 2024-2026 the original author or authors.
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
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

public abstract class AbstractMigrateToHibernateWithHypersistenceTest implements RewriteTest {

    private final String hypersistenceOriginalHibernateVersion;
    private final String hypersistenceExpectedHibernateVersion;
    private final String hypersistenceOriginalVersion;
    private final String hypersistenceExpectedVersionPattern;

    public AbstractMigrateToHibernateWithHypersistenceTest(String hypersistenceOriginalHibernateVersion, String hypersistenceExpectedHibernateVersion, String hypersistenceOriginalVersion, String hypersistenceExpectedVersionPattern) {
        this.hypersistenceOriginalHibernateVersion = hypersistenceOriginalHibernateVersion;
        this.hypersistenceExpectedHibernateVersion = hypersistenceExpectedHibernateVersion;
        this.hypersistenceOriginalVersion = hypersistenceOriginalVersion;
        this.hypersistenceExpectedVersionPattern = hypersistenceExpectedVersionPattern;
    }

    @Test
    void hypersistenceUtilsPackageUpdated() {
        rewriteRun(
          mavenProject(
            "Sample",
            pomXml(
              //language=xml
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>io.hypersistence</groupId>
                      <artifactId>hypersistence-utils-hibernate-%s</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(hypersistenceOriginalHibernateVersion, hypersistenceOriginalVersion),
              spec -> spec.after(actual ->
                assertThat(actual)
                  .contains("<artifactId>hypersistence-utils-hibernate-" + hypersistenceExpectedHibernateVersion + "</artifactId>")
                  .containsPattern("<version>" + hypersistenceExpectedVersionPattern + "</version>")
                  .actual())
            )
          )
        );
    }
}
