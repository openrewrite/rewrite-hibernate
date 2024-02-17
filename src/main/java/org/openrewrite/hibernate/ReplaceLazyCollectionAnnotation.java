/*
 * Copyright 2024 Schweizerische Bundesbahnen SBB
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

import jakarta.persistence.FetchType;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ReplaceLazyCollectionAnnotation extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace `@LazyCollection` with `jakarta.persistence.FetchType`";
    }

    @Override
    public String getDescription() {
        return "Adds the `FetchType` to jakarta annotations and deletes `@LazyCollection`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.hibernate.annotations.LazyCollection", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                maybeAddImport("jakarta.persistence.FetchType");
                maybeRemoveImport("org.hibernate.annotations.LazyCollection");
                maybeRemoveImport("org.hibernate.annotations.LazyCollectionOption");
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                List<J.Annotation> annotations = removeLazyCollectionAnnotation(method.getLeadingAnnotations());
                return super.visitMethodDeclaration(method.withLeadingAnnotations(annotations), ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                    ExecutionContext ctx) {
                List<J.Annotation> annotations = removeLazyCollectionAnnotation(multiVariable.getLeadingAnnotations());
                return super.visitVariableDeclarations(multiVariable.withLeadingAnnotations(annotations), ctx);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation ann = super.visitAnnotation(annotation, ctx);

                JavaType annType = ann.getType();
                if (!(TypeUtils.isOfClassType(annType, "jakarta.persistence.ElementCollection") ||
                      TypeUtils.isOfClassType(annType, "jakarta.persistence.OneToOne") ||
                      TypeUtils.isOfClassType(annType, "jakarta.persistence.OneToMany") ||
                      TypeUtils.isOfClassType(annType, "jakarta.persistence.ManyToOne") ||
                      TypeUtils.isOfClassType(annType, "jakarta.persistence.ManyToMany"))) {
                    // recipe does not apply
                    return ann;
                }

                List<Expression> currentArgs = ann.getArguments();

                boolean fetchArgumentPresent = currentArgs != null && currentArgs.stream()
                        .anyMatch(arg -> {
                            if (arg instanceof J.Assignment) {
                                return ((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName().equals("fetch");
                            }
                            return false;
                        });

                if (fetchArgumentPresent) {
                    // fetch is already set, don't update it
                    return ann;
                }

                J.FieldAccess fetchType = getCursor().getParentOrThrow().getMessage("fetchType");
                if (fetchType == null) {
                    // no mapping found
                    return ann;
                }

                J.Assignment assignment = (J.Assignment)
                        Objects.requireNonNull(((J.Annotation) JavaTemplate.builder("fetch = #{any(jakarta.persistence.FetchType)}")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), ann.getCoordinates().replaceArguments(), fetchType)
                        ).getArguments()).get(0);

                return ann.withArguments(ListUtils.concat(
                        currentArgs,
                        assignment.withPrefix((currentArgs == null || currentArgs.isEmpty()) ? Space.EMPTY : Space.SINGLE_SPACE))
                );
            }

            private List<J.Annotation> removeLazyCollectionAnnotation(List<J.Annotation> annotations) {

                J.Annotation lazyCollectionAnnotation = annotations.stream()
                        .filter(a -> a.getSimpleName().equals("LazyCollection"))
                        .findFirst()
                        .orElse(null);

                if (lazyCollectionAnnotation == null) {
                    return annotations;
                }

                List<Expression> arguments = lazyCollectionAnnotation.getArguments();
                if (arguments == null || arguments.isEmpty()) {
                    // default is LazyCollectionOption.TRUE
                    storeFetchType(FetchType.LAZY);
                } else {
                    switch (arguments.get(0).toString()) {
                        case "LazyCollectionOption.FALSE":
                            storeFetchType(FetchType.EAGER);
                            break;
                        case "LazyCollectionOption.TRUE":
                            storeFetchType(FetchType.LAZY);
                            break;
                        default:
                            // EXTRA can't be mapped to a FetchType; requires refactoring
                            return annotations;
                    }
                }

                return removeAnnotation(annotations, lazyCollectionAnnotation);
            }

            private void storeFetchType(FetchType fetchType) {
                JavaType fetchTypeType = JavaType.buildType("jakarta.persistence.FetchType");
                getCursor().putMessage("fetchType", new J.FieldAccess(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                new J.Identifier(
                                        Tree.randomId(),
                                        Space.SINGLE_SPACE,
                                        Markers.EMPTY,
                                        Collections.emptyList(),
                                        "FetchType",
                                        fetchTypeType,
                                        null
                                ),
                                new JLeftPadded<>(
                                        Space.EMPTY,
                                        new J.Identifier(
                                                Tree.randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                Collections.emptyList(),
                                                fetchType.name(),
                                                fetchTypeType,
                                                null),
                                        Markers.EMPTY),
                        fetchTypeType
                        )
                );
            }

            private List<J.Annotation> removeAnnotation(List<J.Annotation> annotations, J.Annotation target) {
                int index = annotations.indexOf(target);
                List<J.Annotation> newLeadingAnnotations = new ArrayList<>();
                if (index == 0) {
                    // copy prefix of the removed annotation to the next to retain formatting:
                    newLeadingAnnotations.add(annotations.get(1).withPrefix(target.getPrefix()));
                }
                for (int i = (index == 0) ? 2 : 0; i < annotations.size(); i++) {
                    if (i != index) {
                        newLeadingAnnotations.add(annotations.get(i));
                    }
                }
                return newLeadingAnnotations;
            }
        });
    }
}
