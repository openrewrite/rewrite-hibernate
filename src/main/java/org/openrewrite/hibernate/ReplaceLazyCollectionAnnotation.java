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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Optional;

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

                // Do not update existing fetch value
                if (currentArgs != null && currentArgs.stream()
                        .filter(J.Assignment.class::isInstance)
                        .map(J.Assignment.class::cast)
                        .anyMatch(arg -> "fetch".equals(((J.Identifier) arg.getVariable()).getSimpleName()))) {
                    return ann;
                }

                // Retrieve FetchType set from LazyCollectionOption
                String fetchType = getCursor().getNearestMessage("fetchType");
                if (fetchType == null) {
                    // no mapping found
                    return ann;
                }

                maybeAddImport("jakarta.persistence.FetchType", false);
                J.Annotation annotationWithFetch = JavaTemplate.builder("fetch = " + fetchType)
                        .imports("jakarta.persistence.FetchType")
                        .contextSensitive()
                        .build()
                        .apply(getCursor(), ann.getCoordinates().replaceArguments());
                JavaType fetchTypeType = JavaType.buildType("jakarta.persistence.FetchType");
                J.Assignment assignment = (J.Assignment) annotationWithFetch.getArguments().get(0);
                assignment = assignment
                        .withPrefix(currentArgs == null || currentArgs.isEmpty() ? Space.EMPTY : Space.SINGLE_SPACE)
                        .withAssignment(assignment.getAssignment().withType(fetchTypeType))
                        .withType(fetchTypeType);
                return ann.withArguments(ListUtils.concat(currentArgs, assignment));
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
