/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.hibernate.validator;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

public class MoveValidToContainerElement extends Recipe {

    private static final String VALID = "jakarta.validation.Valid";
    private static final AnnotationMatcher VALID_MATCHER = new AnnotationMatcher('@' + VALID);

    @Override
    public String getDisplayName() {
        return "Move `@Valid` from the container to its element type";
    }

    @Override
    public String getDescription() {
        return "Hibernate Validator 9.1 deprecates requesting cascaded validation at the container level, and warns " +
                "while building the metadata for declarations such as `@Valid List<Order> orders`. Rewrites those to " +
                "the type argument form `List<@Valid Order> orders` that has been available since Bean Validation " +
                "2.0. The element type argument is annotated for `Iterable` and `Optional` containers, and the value " +
                "type argument for `Map`, matching where the built-in value extractors cascade to. `@Valid` on " +
                "anything other than one of those containers is left alone, as are wildcard and raw type arguments.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(VALID, true), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                TypeTree movedInward = moveInwardFromTypeExpression(vd.getTypeExpression());
                if (movedInward != null) {
                    return vd.withTypeExpression(movedInward);
                }

                J.Annotation valid = findValid(vd.getLeadingAnnotations());
                if (valid == null || annotateElementType(vd.getTypeExpression(), valid) == null) {
                    return vd;
                }

                List<J.Annotation> remaining = removeAnnotation(vd.getLeadingAnnotations(), valid);
                if (vd.getLeadingAnnotations().get(0) == valid && remaining.isEmpty()) {
                    if (vd.getModifiers().isEmpty()) {
                        vd = vd.withTypeExpression(shiftLeft(vd.getTypeExpression()));
                    } else {
                        vd = vd.withModifiers(Space.formatFirstPrefix(vd.getModifiers(),
                                Space.firstPrefix(vd.getModifiers()).withWhitespace("")));
                    }
                }
                return vd.withLeadingAnnotations(remaining)
                        .withTypeExpression(annotateElementType(vd.getTypeExpression(), valid));
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                TypeTree movedInward = moveInwardFromTypeExpression(m.getReturnTypeExpression());
                if (movedInward != null) {
                    return m.withReturnTypeExpression(movedInward);
                }

                J.Annotation valid = findValid(m.getLeadingAnnotations());
                if (valid == null || annotateElementType(m.getReturnTypeExpression(), valid) == null) {
                    return m;
                }

                List<J.Annotation> remaining = removeAnnotation(m.getLeadingAnnotations(), valid);
                if (m.getLeadingAnnotations().get(0) == valid && remaining.isEmpty()) {
                    if (m.getModifiers().isEmpty()) {
                        m = m.withReturnTypeExpression(shiftLeft(m.getReturnTypeExpression()));
                    } else {
                        m = m.withModifiers(Space.formatFirstPrefix(m.getModifiers(),
                                Space.firstPrefix(m.getModifiers()).withWhitespace("")));
                    }
                }
                return m.withLeadingAnnotations(remaining)
                        .withReturnTypeExpression(annotateElementType(m.getReturnTypeExpression(), valid));
            }
        });
    }

    private static J.@Nullable Annotation findValid(List<J.Annotation> leadingAnnotations) {
        for (J.Annotation annotation : leadingAnnotations) {
            if (VALID_MATCHER.matches(annotation)) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * Drops {@code valid} from the leading annotations, letting whichever annotation followed it take over its
     * prefix so that the declaration keeps its original indentation and line breaks.
     */
    private static List<J.Annotation> removeAnnotation(List<J.Annotation> leadingAnnotations, J.Annotation valid) {
        List<J.Annotation> remaining = ListUtils.map(leadingAnnotations, a -> a == valid ? null : a);
        if (leadingAnnotations.get(0) == valid && !remaining.isEmpty()) {
            return ListUtils.mapFirst(remaining, first -> first.withPrefix(valid.getPrefix()));
        }
        return remaining;
    }

    private static <T extends J> @Nullable T shiftLeft(@Nullable T tree) {
        return tree == null ? null : tree.withPrefix(tree.getPrefix().withWhitespace(""));
    }

    /**
     * Handles {@code private @Valid List<Order> orders}, where the parser attaches the type use annotation to the
     * container type itself rather than to the declaration. Returns the type expression with {@code @Valid} moved
     * onto the element type, or {@code null} when there is nothing to move.
     */
    private static @Nullable TypeTree moveInwardFromTypeExpression(@Nullable TypeTree typeExpression) {
        if (!(typeExpression instanceof J.AnnotatedType)) {
            return null;
        }
        J.AnnotatedType annotatedType = (J.AnnotatedType) typeExpression;
        J.Annotation valid = findValid(annotatedType.getAnnotations());
        if (valid == null) {
            return null;
        }
        TypeTree container = annotateElementType(annotatedType.getTypeExpression(), valid);
        if (container == null) {
            return null;
        }

        List<J.Annotation> remaining = ListUtils.map(annotatedType.getAnnotations(), a -> a == valid ? null : a);
        if (remaining.isEmpty()) {
            // Whichever of the two held the whitespace separating the declaration from `@Valid` now separates it
            // from the container type.
            Space prefix = annotatedType.getPrefix();
            return container.withPrefix(Space.EMPTY.equals(prefix) ? valid.getPrefix() : prefix);
        }
        if (annotatedType.getAnnotations().get(0) == valid) {
            remaining = ListUtils.mapFirst(remaining, first -> first.withPrefix(valid.getPrefix()));
        }
        return annotatedType.withAnnotations(remaining).withTypeExpression(container);
    }

    /**
     * Returns {@code typeExpression} with {@code valid} applied to the type argument the built-in value extractors
     * cascade into, or {@code null} when the declaration should be left as is.
     */
    private static @Nullable TypeTree annotateElementType(@Nullable TypeTree typeExpression, J.Annotation valid) {
        if (!(typeExpression instanceof J.ParameterizedType)) {
            return null;
        }
        J.ParameterizedType parameterized = (J.ParameterizedType) typeExpression;
        int index = elementTypeArgumentIndex(parameterized.getType());
        List<Expression> typeArguments = parameterized.getTypeParameters();
        if (index < 0 || typeArguments == null || typeArguments.size() <= index) {
            return null;
        }

        Expression typeArgument = typeArguments.get(index);
        if (!(typeArgument instanceof TypeTree) || typeArgument instanceof J.Wildcard) {
            return null;
        }
        J.Annotation elementValid = valid.withId(Tree.randomId()).withPrefix(Space.EMPTY);
        Expression annotated;
        if (typeArgument instanceof J.AnnotatedType) {
            J.AnnotatedType annotatedType = (J.AnnotatedType) typeArgument;
            if (findValid(annotatedType.getAnnotations()) != null) {
                return null;
            }
            annotated = annotatedType.withAnnotations(ListUtils.concat(elementValid,
                    ListUtils.mapFirst(annotatedType.getAnnotations(), first -> first.withPrefix(Space.SINGLE_SPACE))));
        } else {
            annotated = new J.AnnotatedType(Tree.randomId(), typeArgument.getPrefix(), Markers.EMPTY,
                    singletonList(elementValid), ((TypeTree) typeArgument).withPrefix(Space.SINGLE_SPACE));
        }

        List<Expression> newTypeArguments = new ArrayList<>(typeArguments);
        newTypeArguments.set(index, annotated);
        return parameterized.withTypeParameters(newTypeArguments);
    }

    /**
     * The index of the type argument that Bean Validation's built-in value extractors cascade into, or {@code -1}
     * for types without such an extractor.
     */
    private static int elementTypeArgumentIndex(@Nullable JavaType type) {
        if (TypeUtils.isAssignableTo("java.util.Map", type)) {
            return 1;
        }
        if (TypeUtils.isAssignableTo("java.lang.Iterable", type) || TypeUtils.isAssignableTo("java.util.Optional", type)) {
            return 0;
        }
        return -1;
    }
}
