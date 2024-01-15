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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TypeAnnotationParameter extends Recipe {

    private static final String FQN_TYPE_ANNOTATION = "org.hibernate.annotations.Type";

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

    private Set<String> IGNORED_FQNS = new HashSet<>(Arrays.asList(
            "org.hibernate.type.EnumType",
            "org.hibernate.type.SerializableType",
            "org.hibernate.type.SerializableToBlobType",
            "org.hibernate.type.TextType"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                if (type != null && FQN_TYPE_ANNOTATION.equals(type.getFullyQualifiedName())) {
                    final boolean isOnlyParameter = a.getArguments().size() == 1;
                    a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            if (assignment.getVariable() instanceof J.Identifier
                                    && "type".equals(((J.Identifier) assignment.getVariable()).getSimpleName())
                                    && assignment.getAssignment() instanceof J.Literal) {
                                J.Identifier paramName = (J.Identifier) assignment.getVariable();
                                String fqTypeName = (String) ((J.Literal) assignment.getAssignment()).getValue();
                                if (IGNORED_FQNS.contains(fqTypeName)) {
                                    return arg;
                                }

                                String simpleTypeName = getSimpleName(fqTypeName);
                                JavaType typeOfNewValue = JavaType.buildType(fqTypeName);
                                J.FieldAccess fa = new J.FieldAccess(
                                        Tree.randomId(),
                                        isOnlyParameter ? Space.EMPTY : assignment.getAssignment().getPrefix(),
                                        assignment.getAssignment().getMarkers(),
                                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), simpleTypeName, typeOfNewValue, null),
                                        JLeftPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "class", null, null)),
                                        JavaType.buildType("java.lang.Class")
                                );
                                maybeAddImport(fqTypeName);
                                if (isOnlyParameter) {
                                    return fa;
                                }
                                return assignment.withVariable(paramName.withSimpleName("value")).withAssignment(fa);
                            }
                        }
                        return arg;
                    }));
                }
                return a;
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
