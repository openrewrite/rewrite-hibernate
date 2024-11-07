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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MigrateResultCheckStyleToExpectation extends Recipe {

    private static final Map<String, String> MAPPING = new HashMap<>();
    private static final Set<AnnotationMatcher> ANNOTATION_MATCHERS;

    static {
        MAPPING.put("ResultCheckStyle.NONE", "org.hibernate.jdbc.Expectation.None.class");
        MAPPING.put("ResultCheckStyle.COUNT", "org.hibernate.jdbc.Expectation.RowCount.class");
        MAPPING.put("ResultCheckStyle.PARAM", "org.hibernate.jdbc.Expectation.OutParameter.class");

        ANNOTATION_MATCHERS = Stream.of("SQLInsert", "SQLUpdate", "SQLDelete", "SQLDeleteAll").map(annotationName ->
                        new AnnotationMatcher("@org.hibernate.annotations." + annotationName, true))
                .collect(Collectors.toSet());
    }

    @Override
    public String getDisplayName() {
        return "Migration of ResultCheckStyle to Expectation";
    }

    @Override
    public String getDescription() {
        return "Will migrate the usage of `org.hibernate.annotations.ResultCheckStyle` to `org.hibernate.jdbc.Expectation`" +
               "in the `@SQLInsert` annotation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return super.visitCompilationUnit(cu, ctx);
                return super.visitCompilationUnit(cu, executionContext);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotationn, ExecutionContext ctx) {
                if (ANNOTATION_MATCHERS.stream().anyMatch(m -> m.matches(annotationn))) {
                    J.Annotation a = super.visitAnnotation(annotationn, ctx);
                    List<Expression> arguments = a.getArguments();

                    for (int i = 0; i < arguments.size(); i++) {
                        if (arguments.get(i) instanceof J.Assignment &&
                            (((J.Assignment) arguments.get(i)).getVariable() instanceof J.Identifier)) {

                            // Each argument should be an assignment
                            J.Assignment assignment = (J.Assignment) arguments.get(i);
                            // Each assignment should have a
                            J.Identifier identifier = (J.Identifier) assignment.getVariable();

                            if ("check".equals(identifier.getSimpleName())) {
                                String map = getMappingForResultCheck(assignment);
                                if (map != null) {
                                    a = JavaTemplate.builder("verify = #{}")
                                            .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                                    "package org.hibernate.jdbc;public class Expectation { public static class None {} }"))
                                            .build()
                                            .apply(getCursor(), assignment.getCoordinates().replace(), map);

                                    maybeAddImport("org.hibernate.jdbc.Expectation");
                                    maybeRemoveImport("org.hibernate.annotations.ResultCheckStyle");
                                    new ShortenFullyQualifiedTypeReferences().getVisitor().visit(a, ctx);
                                    return a;
                                }
                            }
                        }
                    }
                }
                return annotationn;
            }

            private @Nullable String getMappingForResultCheck(J.Assignment assignment) {
                for (Map.Entry<String, String> entry : MAPPING.entrySet()) {
                    if (assignment.getAssignment().toString().contains(entry.getKey())) {
                        return entry.getValue();
                    }
                }
                return null;
            }
        };
    }
}
