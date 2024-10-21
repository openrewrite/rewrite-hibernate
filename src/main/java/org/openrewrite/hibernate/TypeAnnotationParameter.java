/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class TypeAnnotationParameter extends Recipe {

    private static final AnnotationMatcher FQN_TYPE_ANNOTATION = new AnnotationMatcher("@org.hibernate.annotations.Type");

    @Override
    public String getDisplayName() {
        return "@Type annotation type parameter migration";
    }

    @Override
    public String getDescription() {
        return "Hibernate 6.x has 'type' parameter of type String replaced with 'value' of type class.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    private static final Set<String> REMOVED_FQNS = new HashSet<>(Arrays.asList(
            "org.hibernate.type.EnumType",
            "org.hibernate.type.SerializableType",
            "org.hibernate.type.SerializableToBlobType",
            "org.hibernate.type.TextType"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!FQN_TYPE_ANNOTATION.matches(a)) {
                    return a;
                }

                // Remove entire annotation if type is one of the removed types
                if (a.getArguments().stream().anyMatch(arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier &&
                            "type".equals(((J.Identifier) assignment.getVariable()).getSimpleName()) &&
                            assignment.getAssignment() instanceof J.Literal) {
                            String fqTypeName = (String) ((J.Literal) assignment.getAssignment()).getValue();
                            return REMOVED_FQNS.contains(fqTypeName);
                        }
                    }
                    return false;
                })) {
                    maybeRemoveImport("org.hibernate.annotations.Type");
                    return null;
                }

                // Replace with Temporal if applicable
                AtomicReference<String> temporalType = getTemporalTypeArgument(a);
                //noinspection ConstantValue
                if (temporalType.get() != null) {
                    maybeAddImport("jakarta.persistence.Temporal");
                    maybeAddImport("jakarta.persistence.TemporalType");
                    maybeRemoveImport("org.hibernate.annotations.Type");
                    return JavaTemplate.builder("@Temporal(TemporalType." + temporalType.get().toUpperCase() + ")")
                            .doBeforeParseTemplate(System.out::println)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.persistence-api"))
                            .imports("jakarta.persistence.Temporal", "jakarta.persistence.TemporalType")
                            .build()
                            .apply(getCursor(), a.getCoordinates().replace());
                }

                // Replace argument with .class reference to the same type
                return replaceArgumentWithClass(a);
            }

            private AtomicReference<String> getTemporalTypeArgument(J.Annotation a) {
                AtomicReference<String> temporalType = new AtomicReference<>();
                new JavaIsoVisitor<AtomicReference<String>>() {
                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, AtomicReference<String> ref) {
                        J.Assignment as = super.visitAssignment(assignment, ref);
                        if (J.Literal.isLiteralValue(as.getAssignment(), "date") ||
                            J.Literal.isLiteralValue(as.getAssignment(), "time") ||
                            J.Literal.isLiteralValue(as.getAssignment(), "timestamp")) {
                            //noinspection DataFlowIssue
                            ref.set((String) ((J.Literal) as.getAssignment()).getValue());
                        }
                        return as;
                    }
                }.visitNonNull(a, temporalType);
                return temporalType;
            }

            private J.Annotation replaceArgumentWithClass(J.Annotation a) {
                final boolean isOnlyParameter = a.getArguments().size() == 1;
                // Replace type parameter with value parameter
                return a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier &&
                            "type".equals(((J.Identifier) assignment.getVariable()).getSimpleName()) &&
                            assignment.getAssignment() instanceof J.Literal) {
                            String fqTypeName = (String) ((J.Literal) assignment.getAssignment()).getValue();
                            J.Identifier identifier = new J.Identifier(
                                    Tree.randomId(),
                                    Space.EMPTY,
                                    Markers.EMPTY,
                                    Collections.emptyList(),
                                    getSimpleName(fqTypeName),
                                    JavaType.buildType(fqTypeName),
                                    null);
                            J.FieldAccess fa = new J.FieldAccess(
                                    Tree.randomId(),
                                    isOnlyParameter ? Space.EMPTY : assignment.getAssignment().getPrefix(),
                                    assignment.getAssignment().getMarkers(),
                                    identifier,
                                    JLeftPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "class", null, null)),
                                    JavaType.buildType("java.lang.Class")
                            );
                            maybeAddImport(fqTypeName);
                            if (isOnlyParameter) {
                                return fa;
                            }
                            return assignment.withVariable(((J.Identifier) assignment.getVariable()).withSimpleName("value")).withAssignment(fa);
                        }
                    }
                    return arg;
                }));
            }
        };
    }

    private static String getSimpleName(String fqName) {
        int idx = fqName.lastIndexOf('.');
        if (idx > 0 && idx < fqName.length() - 1) {
            return fqName.substring(idx + 1);
        }
        return fqName;
    }

}
