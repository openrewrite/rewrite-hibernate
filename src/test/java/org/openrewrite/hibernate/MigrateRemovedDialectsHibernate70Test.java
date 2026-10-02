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

import static org.openrewrite.properties.Assertions.properties;

class MigrateRemovedDialectsHibernate70Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.hibernate.MigrateRemovedDialectsHibernate70");
    }

    @DocumentExample
    @Test
    void replacesDB2400DialectWithDB2iNotGenericDB2() {
        rewriteRun(
          properties(
            "spring.jpa.database-platform=org.hibernate.dialect.DB2400Dialect\n",
            "spring.jpa.database-platform=org.hibernate.dialect.DB2iDialect\n",
            s -> s.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    void replacesDB2400V7R3DialectWithDB2i() {
        rewriteRun(
          properties(
            "spring.jpa.database-platform=org.hibernate.dialect.DB2400V7R3Dialect\n",
            "spring.jpa.database-platform=org.hibernate.dialect.DB2iDialect\n",
            s -> s.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    void replacesMariaDB106DialectInProperties() {
        rewriteRun(
          properties(
            "spring.jpa.database-platform=org.hibernate.dialect.MariaDB106Dialect\n",
            "spring.jpa.database-platform=org.hibernate.dialect.MariaDBDialect\n",
            s -> s.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    void replacesHANAColumnStoreDialectInProperties() {
        rewriteRun(
          properties(
            "spring.jpa.database-platform=org.hibernate.dialect.HANAColumnStoreDialect\n",
            "spring.jpa.database-platform=org.hibernate.dialect.HANADialect\n",
            s -> s.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    void replacesHANARowStoreDialectInProperties() {
        rewriteRun(
          properties(
            "spring.jpa.database-platform=org.hibernate.dialect.HANARowStoreDialect\n",
            "spring.jpa.database-platform=org.hibernate.dialect.HANADialect\n",
            s -> s.path("src/main/resources/application.properties")
          )
        );
    }
}
