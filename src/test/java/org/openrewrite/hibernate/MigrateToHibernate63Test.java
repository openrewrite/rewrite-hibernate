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

import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;

class MigrateToHibernate63Test extends AbstractMigrateToHibernateWithHypersistenceTest {

    MigrateToHibernate63Test() {
        super("62", "63", "3.7.3", "3\\.15\\.\\d+");
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.hibernate")
          .build()
          .activateRecipes("org.openrewrite.hibernate.MigrateToHibernate63"));
    }
}
