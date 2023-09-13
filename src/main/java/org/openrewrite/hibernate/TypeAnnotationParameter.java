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

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;

public class TypeAnnotationParameter extends Recipe {

    //private static final String FQN_TYPE_ANNOTATION = "org.hibernate.annotations.Type";
    private static final AnnotationMatcher FQN_TYPE_ANNOTATION = new AnnotationMatcher("@org.hibernate.annotations Type(..)");
    private static final AnnotationMatcher TYPE_DEF_MATCHER = new AnnotationMatcher("@org.hibernate.annotations TypeDef(..)");
    private static final AnnotationMatcher TYPE_DEFS_MATCHER = new AnnotationMatcher("@org.hibernate.annotations TypeDefs(..)");

    @Override
    public @NotNull String getDisplayName() {
        return "@Type annotation type parameter migration";
    }

    @Override
    public @NotNull String getDescription() {
        return "Hibernate 6.x has 'type' parameter of type String replaced with 'value' of type class.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }
    //FQN_TYPE_ANNOTATION.equals(type.getFullyQualifiedName())

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public @NotNull J visitAnnotation(J.Annotation annotation, @NotNull ExecutionContext executionContext) {
                J.Annotation a = (J.Annotation)super.visitAnnotation(annotation, executionContext);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                if (type != null) {
                    if (FQN_TYPE_ANNOTATION.matches(a)) {
                        System.out.println("found @Type");
                        return visitTypeAnnotation(a, executionContext);
                    }else if (TYPE_DEF_MATCHER.matches(a) || TYPE_DEFS_MATCHER.matches(a)) {
                        System.out.println("found @TypeDef or @TypeDefs");
                        return visitTypeDefAnnotation(a, executionContext);
                    }
                }
                return a;
            }

            private J visitTypeDefAnnotation(J.Annotation a, ExecutionContext ctx) {
                maybeRemoveImport("org.hibernate.annotations.TypeDef");
                maybeRemoveImport("org.hibernate.annotations.TypeDefs");
                return null;
            }

            private J visitTypeAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                final boolean isOnlyParameter = annotation.getArguments().size() == 1;
                J.Annotation a = annotation;
                a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                    // check if arg is an assignment
                    if (!(arg instanceof J.Assignment)) {
                        return arg;
                    }
                    // check if arg meets assignment conditions
                    J.Assignment assignment = (J.Assignment) arg;
                    if (checkArgumentAssignmentConditions(assignment)) {
                        return annotation;
                    }

                    J.Identifier paramName = (J.Identifier) assignment.getVariable();
                    String fqTypeName = (String) ((J.Literal) assignment.getAssignment()).getValue(); // json
                    boolean fqTypeNameIsJson = fqTypeName.equals("json");
                    fqTypeName = fqTypeNameIsJson ? "JsonType" : fqTypeName;
                    String simpleTypeName = getSimpleName(fqTypeName);
                    JavaType typeOfNewValue = JavaType.buildType(fqTypeName);

                    J.FieldAccess fa = buildFieldAccess(isOnlyParameter, assignment, simpleTypeName, typeOfNewValue);

                    maybeAddImport(fqTypeName);
                    if (isOnlyParameter) {
                        return fa;
                    }
                    return assignment.withVariable(paramName.withSimpleName("value")).withAssignment(fa);
                }));

                return a;
            }

            private J.FieldAccess buildFieldAccess(boolean isOnlyParameter, J.Assignment assignment, String simpleTypeName, JavaType typeOfNewValue) {
                return new J.FieldAccess(
                        Tree.randomId(),
                        isOnlyParameter ? Space.EMPTY : assignment.getAssignment().getPrefix(),
                        assignment.getAssignment().getMarkers(),
                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), simpleTypeName, typeOfNewValue, null),
                        JLeftPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "class", null, null)),
                        JavaType.buildType("java.lang.Class")
                );
            }

            private boolean checkArgumentAssignmentConditions(J.Assignment assignment) {
                return !(assignment.getVariable() instanceof J.Identifier
                         && "type".equals(((J.Identifier) assignment.getVariable()).getSimpleName())
                         && assignment.getAssignment() instanceof J.Literal);
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
