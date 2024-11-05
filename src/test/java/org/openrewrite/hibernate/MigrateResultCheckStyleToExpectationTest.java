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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateResultCheckStyleToExpectationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveInvalidHibernateGeneratedValueAnnotation())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-6.5+")
          );
    }

    @DocumentExample
    @Test
    void migrateSqlInsertNoneToExpectationNone() {
        // language=java
        rewriteRun(
          java(
          """
            import org.hibernate.engine.spi.ResultCheckStyle;

            @SQLInsert(check = ResultCheckStyle.NONE)
            class A {}
            """, """
            import org.hibernate.annotations.SQLInsert;
            import org.hibernate.jdbc.Expectation;

            @SQLInsert(verify = Expectation.None.class, sql = "")
            class A {}
            """
          ));
    }

    @Test
    void migrateSqlInsertCountToExpectationRowCount() {
        // language=java
        rewriteRun(
          java(
          """
            import org.hibernate.annotations.SQLInsert;
            import org.hibernate.engine.spi.ResultCheckStyle;

            @SQLInsert(check = ResultCheckStyle.COUNT, sql = "")
            class A {}
            """, """
            import org.hibernate.annotations.SQLInsert;
            import org.hibernate.jdbc.Expectation;

            @SQLInsert(verify = Expectation.RowCount.class, sql = "")
            class A {}
            """
          ));
    }

    @Test
    void migrateSqlInsertParamToExpectationOutParameter() {
        // language=java
        rewriteRun(
          java(
          """
            import org.hibernate.annotations.SQLInsert;
            import org.hibernate.engine.spi.ResultCheckStyle;

            @SQLInsert(check = ResultCheckStyle.PARAM, sql = "")
            class A {}
            """, """
            import org.hibernate.annotations.SQLInsert;
            import org.hibernate.jdbc.Expectation;

            @SQLInsert(verify = Expectation.OutParameter.class, sql = "")
            class A {}
            """
          ));
    }
}
