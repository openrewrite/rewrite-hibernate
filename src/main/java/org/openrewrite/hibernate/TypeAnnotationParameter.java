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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.Markup;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;

public class TypeAnnotationParameter extends Recipe {

    private static final String ORG_HIBERNATE_ANNOTATIONS_TYPE = "org.hibernate.annotations.Type";
    private static final AnnotationMatcher FQN_TYPE_ANNOTATION = new AnnotationMatcher("@" + ORG_HIBERNATE_ANNOTATIONS_TYPE);
    private static final String ORG_HIBERNATE_ANNOTATIONS_TYPEDEF = "org.hibernate.annotations.TypeDef";
    private static final AnnotationMatcher FQN_TYPEDEF_ANNOTATION = new AnnotationMatcher("@" + ORG_HIBERNATE_ANNOTATIONS_TYPEDEF);
    private static final String ORG_HIBERNATE_ANNOTATIONS_TYPEDEFS = "org.hibernate.annotations.TypeDefs";
    private static final AnnotationMatcher FQN_TYPEDEFS_ANNOTATION = new AnnotationMatcher("@" + ORG_HIBERNATE_ANNOTATIONS_TYPEDEFS);

    @Override
    public String getDisplayName() {
        return "`@Type` annotation type parameter migration";
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
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (FQN_TYPEDEF_ANNOTATION.matches(a)) {
                    Expression name = getAttributeValue(annotation, "name");
                    if (name instanceof J.Literal) {
                        String alias = (String) ((J.Literal) name).getValue();
                        Expression typeClass = getAttributeValue(annotation, "typeClass");
                        getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, alias, typeClass);
                    }
                    // Always remove @TypeDef
                    maybeRemoveImport(ORG_HIBERNATE_ANNOTATIONS_TYPEDEF);
                    return null;
                }

                if (FQN_TYPEDEFS_ANNOTATION.matches(a)) {
                    if (a.getArguments() == null || a.getArguments().isEmpty() || a.getArguments().get(0) instanceof J.Empty) {
                        maybeRemoveImport(ORG_HIBERNATE_ANNOTATIONS_TYPEDEFS);
                        return null;
                    }
                    return Markup.warn(a, new IllegalStateException("Could not convert @TypeDefs annotation automatically"));
                }

                if (!FQN_TYPE_ANNOTATION.matches(a)) {
                    return a;
                }

                // Remove entire annotation if type is one of the removed types
                if (a.getArguments() != null && a.getArguments().stream().anyMatch(arg -> {
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
                    maybeRemoveImport(ORG_HIBERNATE_ANNOTATIONS_TYPE);
                    return null;
                }

                // Replace with Temporal if applicable
                AtomicReference<String> temporalType = getTemporalTypeArgument(a);
                //noinspection ConstantValue
                if (temporalType.get() != null) {
                    maybeRemoveImport(ORG_HIBERNATE_ANNOTATIONS_TYPE);
                    maybeAddImport("jakarta.persistence.Temporal");
                    maybeAddImport("jakarta.persistence.TemporalType");
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

            private @Nullable Expression getAttributeValue(J.Annotation annotation, String attributeName) {
                List<Expression> arguments = annotation.getArguments();
                if (arguments == null) {
                    return null;
                }
                for (Expression arg : arguments) {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier &&
                                attributeName.equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                            return assignment.getAssignment();
                        }
                    }
                }
                return null;
            }

            private AtomicReference<@Nullable String> getTemporalTypeArgument(J.Annotation a) {
                return new JavaIsoVisitor<AtomicReference<@Nullable String>>() {
                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, AtomicReference<@Nullable String> ref) {
                        J.Assignment as = super.visitAssignment(assignment, ref);
                        if (J.Literal.isLiteralValue(as.getAssignment(), "date") ||
                                J.Literal.isLiteralValue(as.getAssignment(), "time") ||
                                J.Literal.isLiteralValue(as.getAssignment(), "timestamp")) {
                            ref.set((String) ((J.Literal) as.getAssignment()).getValue());
                        }
                        return as;
                    }
                }.reduce(a, new AtomicReference<>());
            }

            private J.Annotation replaceArgumentWithClass(J.Annotation a) {
                final boolean isOnlyParameter = a.getArguments() != null && a.getArguments().size() == 1;
                // Replace type parameter with value parameter
                return a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier &&
                                "type".equals(((J.Identifier) assignment.getVariable()).getSimpleName()) &&
                                assignment.getAssignment() instanceof J.Literal) {
                            String fqTypeName = (String) ((J.Literal) assignment.getAssignment()).getValue();

                            Expression nearestMessage = getCursor().getNearestMessage(fqTypeName);
                            Expression classRef =
                                    nearestMessage != null && nearestMessage.getType() != JavaType.Unknown.getInstance() ?
                                            nearestMessage :
                                            buildClassReference(
                                                    nearestMessage != null ? getFullyQualifiedTypeName(nearestMessage) : fqTypeName,
                                                    isOnlyParameter ? Space.EMPTY : assignment.getAssignment().getPrefix()
                                            );

                            if (isOnlyParameter) {
                                return classRef.withPrefix(Space.EMPTY);
                            }
                            return assignment
                                    .withVariable(((J.Identifier) assignment.getVariable()).withSimpleName("value"))
                                    .withAssignment(classRef);
                        }
                    }
                    return arg;
                }));
            }

            private J.FieldAccess buildCommonJavaLangClassReference(String simpleName, String fullyQualifiedName, Space prefix) {
                J.Identifier identifier = new J.Identifier(
                        Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        simpleName,
                        JavaType.buildType(fullyQualifiedName),
                        null
                );
                return new J.FieldAccess(
                        Tree.randomId(),
                        prefix,
                        Markers.EMPTY,
                        identifier,
                        JLeftPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "class", null, null)),
                        JavaType.buildType("java.lang.Class")
                );
            }

            private J.FieldAccess buildClassReference(String fullyQualifiedName, Space prefix) {
                String javaLangClassName = fullyQualifiedName.startsWith("java.lang.") ?
                        fullyQualifiedName :
                        TypeUtils.findQualifiedJavaLangTypeName(fullyQualifiedName);
                if (javaLangClassName != null) {
                    return buildCommonJavaLangClassReference(
                            getSimpleName(fullyQualifiedName),
                            fullyQualifiedName,
                            prefix);
                }
                String[] parts = fullyQualifiedName.split("\\.");
                Expression current = new J.Identifier(
                        Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        parts[0],
                        null,
                        null
                );

                for (int i = 1; i < parts.length; i++) {
                    current = new J.FieldAccess(
                            Tree.randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            current,
                            JLeftPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), parts[i], null, null)),
                            null
                    );
                }

                // Add .class at the end
                return new J.FieldAccess(
                        Tree.randomId(),
                        prefix,
                        Markers.EMPTY,
                        current,
                        JLeftPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "class", null, null)),
                        JavaType.buildType("java.lang.Class")
                );
            }
        };
        return Preconditions.check(new UsesType<>(ORG_HIBERNATE_ANNOTATIONS_TYPE, false), visitor);
    }

    private static @Nullable String getFullyQualifiedTypeName(Expression expr) {
        if (expr instanceof J.FieldAccess) {
            JavaType.Parameterized parameterizedType = TypeUtils.asParameterized((expr).getType());
            if (parameterizedType != null) {
                JavaType typeArgument = parameterizedType.getTypeParameters().get(0);
                JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(typeArgument);
                if (fqType != null) {
                    return fqType.getFullyQualifiedName();
                }
            }
            return stripClassSuffix(((J.FieldAccess) expr).toString());
        }
        return null;
    }

    private static String stripClassSuffix(String fqName) {
        return fqName.endsWith(".class") ? fqName.substring(0, fqName.length() - 6) : fqName;
    }

    private static String getSimpleName(String fqName) {
        int idx = fqName.lastIndexOf('.');
        if (idx > 0 && idx < fqName.length() - 1) {
            return fqName.substring(idx + 1);
        }
        return fqName;
    }

}
