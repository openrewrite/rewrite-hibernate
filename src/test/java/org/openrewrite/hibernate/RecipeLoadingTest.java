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
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RecipeLoadingTest implements RewriteTest {

    @Test
    void recipeLoading() {
        List<Recipe> recipes = Environment.builder().scanRuntimeClasspath("org.openrewrite.hibernate").build().listRecipes();
        assertThat(recipes).isNotEmpty();
        for (Recipe recipe : recipes) {
            if (recipe.getName().startsWith("org.openrewrite.hibernate")) {
                // The mapping helper has required options; composites supply all 33 real mappings.
                Recipe configured = recipe instanceof ChangeHibernateDialect ?
                  new ChangeHibernateDialect("org.hibernate.dialect.MySQL8Dialect", "org.hibernate.dialect.MySQLDialect") : recipe;
                assertThatCode(() -> rewriteRun(spec -> spec.recipe(configured)))
                  .as("Recipe %s configures with its required options", recipe.getName()).doesNotThrowAnyException();
            }
        }
    }
}
