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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateDialectTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.MigrateDialect");
    }

    @DocumentExample
    @Test
    void replacesMySQL5DialectInYaml() {
        rewriteRun(
          yaml(
            """
            spring:
              jpa:
                database-platform: org.hibernate.dialect.MySQL5Dialect
                properties:
                  hibernate:
                    dialect: org.hibernate.dialect.MySQL5Dialect
            """,
            """
            spring:
              jpa:
                database-platform: org.hibernate.dialect.MySQLDialect
                properties:
                  hibernate:
                    dialect: org.hibernate.dialect.MySQLDialect
            """,
            s -> s.path("src/main/resources/application.yml")
          )
        );
    }

    @Test
    void replacesMySQL5DialectInProperties() {
        rewriteRun(
          properties(
            """
            spring.jpa.database-platform=org.hibernate.dialect.MySQL5Dialect
            spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL5Dialect
            """,
            """
            spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
            spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
            """,
            s -> s.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    void replacesPostgreSQL95DialectInYaml() {
        rewriteRun(
          yaml(
            """
            spring:
              jpa:
                database-platform: org.hibernate.dialect.PostgreSQL95Dialect
            """,
            """
            spring:
              jpa:
                database-platform: org.hibernate.dialect.PostgreSQLDialect
            """,
            s -> s.path("src/main/resources/application.yml")
          )
        );
    }

    @Test
    void replacesOracle12cDialectInYaml() {
        rewriteRun(
          yaml(
            """
            spring:
              jpa:
                database-platform: org.hibernate.dialect.Oracle12cDialect
            """,
            """
            spring:
              jpa:
                database-platform: org.hibernate.dialect.OracleDialect
            """,
            s -> s.path("src/main/resources/application.yml")
          )
        );
    }

    @Test
    void noChangeWhenAlreadyGeneric() {
        rewriteRun(
          yaml(
            """
            spring:
              jpa:
                database-platform: org.hibernate.dialect.MySQLDialect
            """,
            s -> s.path("src/main/resources/application.yml")
          )
        );
    }
}
