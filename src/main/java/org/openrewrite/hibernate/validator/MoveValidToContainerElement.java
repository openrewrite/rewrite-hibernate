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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.singletonList;

public class MoveValidToContainerElement extends Recipe {

    private static final String VALID = "jakarta.validation.Valid";
    private static final AnnotationMatcher VALID_MATCHER = new AnnotationMatcher('@' + VALID);

    @Getter
    final String displayName = "Move `@Valid` from the container to its element type";

    @Getter
    final String description = "Hibernate Validator 9.1 deprecates requesting cascaded validation at the container level, and warns " +
            "while building the metadata for declarations such as `@Valid List<Order> orders`. Rewrites those to " +
            "the type argument form `List<@Valid Order> orders` that has been available since Bean Validation " +
            "2.0. The element type argument is annotated for `Iterable` and `Optional` containers, and the value " +
            "type argument for `Map`, matching where the built-in value extractors cascade to. `@Valid` on " +
            "anything other than one of those containers is left alone, as are wildcard, raw, qualified and " +
            "array type arguments.";

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
                if (valid == null) {
                    return vd;
                }
                TypeTree annotatedElement = annotateElementType(vd.getTypeExpression(), valid);
                if (annotatedElement == null) {
                    return vd;
                }
                doAfterVisit(new RemoveAnnotationVisitor(sameAnnotation(valid)));
                return vd.withTypeExpression(annotatedElement);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                TypeTree movedInward = moveInwardFromTypeExpression(m.getReturnTypeExpression());
                if (movedInward != null) {
                    return m.withReturnTypeExpression(movedInward);
                }

                J.Annotation valid = findValid(m.getLeadingAnnotations());
                if (valid == null) {
                    return m;
                }
                TypeTree annotatedElement = annotateElementType(m.getReturnTypeExpression(), valid);
                if (annotatedElement == null) {
                    return m;
                }
                doAfterVisit(new RemoveAnnotationVisitor(sameAnnotation(valid)));
                return m.withReturnTypeExpression(annotatedElement);
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
     * Matches only the one annotation instance that was found, so that {@link RemoveAnnotationVisitor} removes the
     * container level {@code @Valid} without also stripping any {@code @Valid} already present on a type argument.
     */
    private static AnnotationMatcher sameAnnotation(J.Annotation annotation) {
        return new AnnotationMatcher('@' + VALID) {
            @Override
            public boolean matches(J.Annotation anno) {
                return annotation.equals(anno);
            }
        };
    }

    /**
     * Drops {@code valid} from the annotations, letting whichever annotation followed it take over its prefix so that
     * the declaration keeps its original indentation and line breaks.
     */
    private static List<J.Annotation> removeAnnotation(List<J.Annotation> annotations, J.Annotation valid) {
        List<J.Annotation> remaining = ListUtils.map(annotations, a -> a == valid ? null : a);
        if (annotations.get(0) == valid && !remaining.isEmpty()) {
            return ListUtils.mapFirst(remaining, first -> first.withPrefix(valid.getPrefix()));
        }
        return remaining;
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

        List<J.Annotation> remaining = removeAnnotation(annotatedType.getAnnotations(), valid);
        if (remaining.isEmpty()) {
            // Whichever of the two held the whitespace separating the declaration from `@Valid` now separates it
            // from the container type, while any comments trailing `@Valid` are kept.
            Space prefix = annotatedType.getPrefix();
            Space kept = prefix.isEmpty() ? valid.getPrefix() : prefix;
            return container.withPrefix(kept.withComments(
                    ListUtils.concatAll(kept.getComments(), container.getPrefix().getComments())));
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
        List<Expression> typeArguments = parameterized.getTypeParameters();
        if (typeArguments == null) {
            return null;
        }
        int index = elementTypeArgumentIndex(parameterized.getType(), typeArguments.size());
        if (index < 0) {
            return null;
        }

        Expression typeArgument = typeArguments.get(index);
        if (!(typeArgument instanceof TypeTree) || !canAnnotateInPlace(typeArgument)) {
            return null;
        }
        Expression annotated;
        if (typeArgument instanceof J.AnnotatedType) {
            J.AnnotatedType annotatedType = (J.AnnotatedType) typeArgument;
            if (findValid(annotatedType.getAnnotations()) != null) {
                return null;
            }
            annotated = annotatedType.withAnnotations(ListUtils.concat(elementValid(valid),
                    ListUtils.mapFirst(annotatedType.getAnnotations(), first -> first.withPrefix(Space.SINGLE_SPACE))));
        } else {
            annotated = new J.AnnotatedType(Tree.randomId(), typeArgument.getPrefix(), Markers.EMPTY,
                    singletonList(elementValid(valid)), ((TypeTree) typeArgument).withPrefix(Space.SINGLE_SPACE));
        }
        return parameterized.withTypeParameters(ListUtils.map(typeArguments, (i, arg) -> i == index ? annotated : arg));
    }

    private static J.Annotation elementValid(J.Annotation valid) {
        return valid.withId(Tree.randomId()).withPrefix(Space.EMPTY);
    }

    /**
     * Peels the wrappers off a type argument until the name it applies to is reached; only a simple name can take a
     * type use annotation. Annotating a qualified name such as {@code Order.Item} does not compile, and annotating an
     * array type such as {@code Order[]} would bind to the component type rather than to the type argument.
     */
    private static boolean canAnnotateInPlace(J typeArgument) {
        if (typeArgument instanceof J.AnnotatedType) {
            return canAnnotateInPlace(((J.AnnotatedType) typeArgument).getTypeExpression());
        }
        if (typeArgument instanceof J.ParameterizedType) {
            return canAnnotateInPlace(((J.ParameterizedType) typeArgument).getClazz());
        }
        return typeArgument instanceof J.Identifier;
    }

    /**
     * The index of the type argument that Bean Validation's built-in value extractors cascade into, or {@code -1}
     * for types without such an extractor. Only containers that declare exactly the type parameters of {@code Map},
     * {@code Iterable} or {@code Optional} are considered, as any other arity means the type arguments no longer line
     * up positionally with the ones the extractors read.
     */
    private static int elementTypeArgumentIndex(@Nullable JavaType type, int arity) {
        if (arity == 2 && TypeUtils.isAssignableTo("java.util.Map", type)) {
            return 1;
        }
        if (arity == 1 && (TypeUtils.isOfClassType(type, "java.util.Optional") || TypeUtils.isAssignableTo("java.lang.Iterable", type))) {
            return 0;
        }
        return -1;
    }
}
