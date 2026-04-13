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

import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;

public class MigrateDialect extends Recipe {

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Migrate Hibernate dialect to the generic dialect";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Migrate all Hibernate version-specific dialect classes " +
                "to their generic equivalents. Version-specific dialects " +
                "were deprecated in Hibernate 6.0 and removed in Hibernate 6.2.";
    }
}
