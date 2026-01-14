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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class MigrateResultCheckStyleToExpectation extends Recipe {

    private static final Map<String, String> MAPPING = new HashMap<>();
    private static final Set<AnnotationMatcher> ANNOTATION_MATCHERS;

    static {
        MAPPING.put("NONE", "org.hibernate.jdbc.Expectation.None.class");
        MAPPING.put("COUNT", "org.hibernate.jdbc.Expectation.RowCount.class");
        MAPPING.put("PARAM", "org.hibernate.jdbc.Expectation.OutParameter.class");

        ANNOTATION_MATCHERS =
                Stream.of("SQLInsert", "SQLUpdate", "SQLDelete", "SQLDeleteAll")
                .map(annotationName -> new AnnotationMatcher("@org.hibernate.annotations." + annotationName, true))
                .collect(toSet());
    }

    @Getter
    final String displayName = "Migration of `ResultCheckStyle` to `Expectation`";

    @Getter
    final String description = "Will migrate the usage of `org.hibernate.annotations.ResultCheckStyle` to `org.hibernate.jdbc.Expectation` " +
      "in `@SQLInsert`, `@SqlUpdate`, `@SqlDelete` and `@SqlDeleteAll` annotations.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.hibernate.annotations.ResultCheckStyle", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation an = super.visitAnnotation(annotation, ctx);
                for (AnnotationMatcher m : ANNOTATION_MATCHERS) {
                    if (m.matches(an)) {
                        return processAnnotation(an, ctx);
                    }
                }
                return an;
            }

            private J.Annotation processAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                List<Expression> arguments = annotation.getArguments();
                if (arguments == null) {
                    return annotation;
                }

                for (Expression argument : arguments) {
                    if (argument instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) argument;
                        if (isAssignmentToCheckParameter(assignment)) {
                            return updateAnnotation(annotation, assignment, ctx);
                        }
                    }
                }
                return annotation;
            }

            private boolean isAssignmentToCheckParameter(J.Assignment assignment) {
                return assignment.getVariable() instanceof J.Identifier &&
                       "check".equals(((J.Identifier) assignment.getVariable()).getSimpleName());
            }

            private J.Annotation updateAnnotation(J.Annotation annotation, J.Assignment assignment, ExecutionContext ctx) {
                String map = getMappingForResultCheck(assignment);
                if (map != null) {
                    return applyTemplate(assignment, map, ctx);
                }
                return annotation;
            }

            private J.Annotation applyTemplate(J.Assignment assignment, String map, ExecutionContext ctx) {
                J.Annotation updatedAnnotation = JavaTemplate.builder("verify = #{}")
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.hibernate.jdbc;public class Expectation { public static class None {} }"))
                        .build()
                        .apply(getCursor(), assignment.getCoordinates().replace(), map);

                maybeRemoveImport("org.hibernate.annotations.ResultCheckStyle");
                maybeAddImport("org.hibernate.jdbc.Expectation");
                doAfterVisit(new ShortenFullyQualifiedTypeReferences().getVisitor());
                return updatedAnnotation;
            }

            private @Nullable String getMappingForResultCheck(J.Assignment assignment) {
                Expression value = assignment.getAssignment();
                if (value instanceof J.FieldAccess) {
                    return MAPPING.get(((J.FieldAccess) value).getSimpleName());
                }
                if (value instanceof J.Identifier) {
                    return MAPPING.get(((J.Identifier) value).getSimpleName());
                }
                return null;
            }
        });
    }
}
