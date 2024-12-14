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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;

public class RemoveInvalidHibernateGeneratedValueAnnotation extends Recipe {

    private static final AnnotationMatcher MATCHER_GENERATED_VALUE_ANNOTATION
            = new AnnotationMatcher("@jakarta.persistence.GeneratedValue", true);
    private static final AnnotationMatcher MATCHER_ID_ANNOTATION
            = new AnnotationMatcher("@jakarta.persistence.Id", true);

    @Override
    public String getDisplayName() {
        return "Remove invalid `@GeneratedValue` annotation";
    }

    @Override
    public String getDescription() {
        return "Removes `@GeneratedValue` annotation from fields that are not also annotated with `@Id`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("jakarta.persistence.GeneratedValue", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        if (MATCHER_GENERATED_VALUE_ANNOTATION.matches(annotation) &&
                            !service(AnnotationService.class).matches(getCursor().getParentTreeCursor(), MATCHER_ID_ANNOTATION)) {
                            doAfterVisit(new RemoveAnnotationVisitor(specificAnnotationMatcher(annotation)));
                        }
                        return annotation;
                    }

                    private AnnotationMatcher specificAnnotationMatcher(J.Annotation annotation) {
                        return new AnnotationMatcher(
                                // ignored in practice, as we only match annotations previously found just above
                                "@jakarta.persistence.GeneratedValue", true) {
                            @Override
                            public boolean matches(J.Annotation anno) {
                                return annotation.equals(anno);
                            }
                        };
                    }
                });
    }
}
