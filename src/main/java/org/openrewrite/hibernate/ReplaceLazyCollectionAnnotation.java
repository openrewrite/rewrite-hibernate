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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.*;

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
                maybeRemoveImport("org.hibernate.annotations.LazyCollection");
                maybeRemoveImport("org.hibernate.annotations.LazyCollectionOption");
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration j = removeLazyCollectionAnnotation(method, ctx);
                return super.visitMethodDeclaration(j, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations j = removeLazyCollectionAnnotation(multiVariable, ctx);
                return super.visitVariableDeclarations(j, ctx);
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

                String fetchType = getCursor().getParentOrThrow().getMessage("fetchType");
                if (fetchType == null) {
                    // no mapping found
                    return ann;
                }

                maybeAddImport("jakarta.persistence.FetchType", false);
                J.Assignment assignment = (J.Assignment)
                        Objects.requireNonNull(((J.Annotation) JavaTemplate.builder("fetch = " + fetchType)
                                .imports("jakarta.persistence.FetchType")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), ann.getCoordinates().replaceArguments())
                        ).getArguments()).get(0);

                return ann.withArguments(ListUtils.concat(
                        currentArgs,
                        assignment.withPrefix((currentArgs == null || currentArgs.isEmpty()) ? Space.EMPTY : Space.SINGLE_SPACE))
                );
            }

            private <T extends J> T removeLazyCollectionAnnotation(T tree, ExecutionContext ctx) {
                Optional<J.Annotation> lazyAnnotation = FindAnnotations.find(tree, "org.hibernate.annotations.LazyCollection")
                        .stream().findFirst();
                if (!lazyAnnotation.isPresent()) {
                    return tree;
                }

                // Capture the FetchType from the LazyCollectionOption
                List<Expression> arguments = lazyAnnotation.get().getArguments();
                if (arguments == null || arguments.isEmpty()) {
                    // default is LazyCollectionOption.TRUE
                    getCursor().putMessage("fetchType", "FetchType.LAZY");
                } else {
                    switch (arguments.get(0).toString()) {
                        case "LazyCollectionOption.FALSE":
                            getCursor().putMessage("fetchType", "FetchType.EAGER");
                            break;
                        case "LazyCollectionOption.TRUE":
                            getCursor().putMessage("fetchType", "FetchType.LAZY");
                            break;
                        default:
                            // EXTRA can't be mapped to a FetchType; requires refactoring
                            return tree;
                    }
                }
                //noinspection unchecked
                return (T) new RemoveAnnotationVisitor(new AnnotationMatcher("@org.hibernate.annotations.LazyCollection"))
                        .visit(tree, ctx, getCursor());
            }
        });
    }
}
