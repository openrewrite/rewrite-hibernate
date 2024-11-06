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

import org.jspecify.annotations.NonNull;
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

import java.util.HashSet;
import java.util.Set;

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
                    public J preVisit(@NonNull J tree, ExecutionContext executionContext) {
                        stopAfterPreVisit();

                        Set<J.Annotation> invalidAnnotations = new RemoveInvalidHibernateGeneratedValueAnnotationVisitor().reduce(tree, new HashSet<>());
                        if (!invalidAnnotations.isEmpty()) {
                            AnnotationMatcher customMatcher = new AnnotationMatcher(
                                    // ignored in practice, as we only match annotations previously found just above
                                    "@jakarta.persistence.GeneratedValue", true) {
                                @Override
                                public boolean matches(J.Annotation annotation) {
                                    return invalidAnnotations.contains(annotation);
                                }
                            };
                            doAfterVisit(new RemoveAnnotationVisitor(customMatcher));
                        }
                        return tree;
                    }
                });
    }

    static class RemoveInvalidHibernateGeneratedValueAnnotationVisitor extends JavaIsoVisitor<Set<J.Annotation>> {
        @Override
        public J.Annotation visitAnnotation(J.Annotation a, Set<J.Annotation> accumulator) {
            if (MATCHER_GENERATED_VALUE_ANNOTATION.matches(a) && !containsBoth()) {
                accumulator.add(a);
            }
            return a;
        }

        private boolean containsBoth() {
            return service(AnnotationService.class)
                    .getAllAnnotations(getCursor().getParentOrThrow())
                    .stream()
                    .anyMatch(MATCHER_ID_ANNOTATION::matches);
        }
    }
}
