// SPDX-License-Identifier: Apache-2.0
// Copyright Red Hat Inc. and Hibernate Authors
package org.openrewrite.hibernate.jpa;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Migrates DML-oriented {@code @NamedQuery} and {@code @NamedNativeQuery}
 * annotations to JPA 4.0's {@code @NamedStatement} and {@code @NamedNativeStatement}.
 */
public class MigrateNamedQueryToStatement extends Recipe {

    private static final String NAMED_QUERY_FQN            = "jakarta.persistence.NamedQuery";
    private static final String NAMED_NATIVE_QUERY_FQN     = "jakarta.persistence.NamedNativeQuery";
    private static final String NAMED_QUERIES_FQN          = "jakarta.persistence.NamedQueries";
    private static final String NAMED_NATIVE_QUERIES_FQN   = "jakarta.persistence.NamedNativeQueries";
    private static final String NAMED_STATEMENT_FQN        = "jakarta.persistence.NamedStatement";
    private static final String NAMED_NATIVE_STATEMENT_FQN = "jakarta.persistence.NamedNativeStatement";

    @Override
    public String getDisplayName() {
        return "Migrate DML @NamedQuery/@NamedNativeQuery to JPA 4.0 @NamedStatement/@NamedNativeStatement";
    }

    @Override
    public String getDescription() {
        return "Renames @NamedQuery and @NamedNativeQuery to @NamedStatement and @NamedNativeStatement " +
                "when the query is a DML statement (DELETE, UPDATE, INSERT). SELECT queries are unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(NAMED_QUERY_FQN, false),
                        new UsesType<>(NAMED_NATIVE_QUERY_FQN, false),
                        new UsesType<>(NAMED_QUERIES_FQN, false),
                        new UsesType<>(NAMED_NATIVE_QUERIES_FQN, false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                        J.ClassDeclaration c = processClassAnnotations(cd, ctx);
                        return super.visitClassDeclaration(c, ctx);
                    }

                    private J.ClassDeclaration processClassAnnotations(J.ClassDeclaration cd, ExecutionContext ctx) {
                        List<J.Annotation> result = new ArrayList<>();
                        boolean anyChange = false;
                        boolean namedQueryStillUsed = false;

                        for (J.Annotation ann : cd.getLeadingAnnotations()) {
                            String fqn = fqnOf(ann);

                            if (NAMED_QUERIES_FQN.equals(fqn) || NAMED_NATIVE_QUERIES_FQN.equals(fqn)) {
                                boolean isNativeContainer = NAMED_NATIVE_QUERIES_FQN.equals(fqn);
                                String entryFqn     = isNativeContainer ? NAMED_NATIVE_QUERY_FQN     : NAMED_QUERY_FQN;
                                String targetFqn    = isNativeContainer ? NAMED_NATIVE_STATEMENT_FQN : NAMED_STATEMENT_FQN;
                                String containerFqn = isNativeContainer ? NAMED_NATIVE_QUERIES_FQN   : NAMED_QUERIES_FQN;

                                List<J.Annotation> entries = extractArrayEntries(ann);
                                if (entries.isEmpty()) {
                                    result.add(ann);
                                    continue;
                                }

                                List<J.Annotation> keepInContainer = new ArrayList<>();
                                List<J.Annotation> extractedDml    = new ArrayList<>();
                                for (J.Annotation entry : entries) {
                                    if (isDml(entry)) {
                                        extractedDml.add(renameQueryAttribute(
                                                renameAnnotationType(entry, entryFqn, targetFqn)));
                                        anyChange = true;
                                    } else {
                                        keepInContainer.add(entry);
                                    }
                                }

                                if (!anyChange) {
                                    result.add(ann);
                                    continue;
                                }

                                if (!keepInContainer.isEmpty()) {
                                    result.add(rebuildContainer(ann, keepInContainer));
                                    namedQueryStillUsed = namedQueryStillUsed || !isNativeContainer;
                                } else {
                                    maybeRemoveImport(containerFqn);
                                    maybeRemoveImport(entryFqn);
                                }
                                result.addAll(extractedDml);
                                continue;
                            }

                            if (NAMED_QUERY_FQN.equals(fqn) && isDml(ann)) {
                                result.add(renameQueryAttribute(renameAnnotationType(ann, NAMED_QUERY_FQN, NAMED_STATEMENT_FQN)));
                                anyChange = true;
                                continue;
                            }

                            if (NAMED_NATIVE_QUERY_FQN.equals(fqn) && isDml(ann)) {
                                result.add(renameQueryAttribute(renameAnnotationType(ann, NAMED_NATIVE_QUERY_FQN, NAMED_NATIVE_STATEMENT_FQN)));
                                anyChange = true;
                                continue;
                            }

                            result.add(ann);
                        }

                        if (!anyChange) {
                            return cd;
                        }
                        if (namedQueryStillUsed) {
                            maybeAddImport(NAMED_QUERY_FQN);
                        }

                        Space firstPrefix = cd.getLeadingAnnotations().get(0).getPrefix();
                        List<J.Annotation> normalized = ListUtils.map(result, (i, a) ->
                                i == 0 ? a.withPrefix(firstPrefix) : a.withPrefix(Space.build("\n", Collections.emptyList())));
                        return cd.withLeadingAnnotations(normalized);
                    }

                    private String fqnOf(J.Annotation a) {
                        return a.getType() == null ? null : a.getType().toString();
                    }

                    private boolean isDml(J.Annotation a) {
                        if (a.getArguments() == null) {
                            return false;
                        }
                        for (Expression arg : a.getArguments()) {
                            if (!(arg instanceof J.Assignment)) {
                                continue;
                            }
                            J.Assignment assignment = (J.Assignment) arg;
                            if (!(assignment.getVariable() instanceof J.Identifier)) {
                                continue;
                            }
                            J.Identifier key = (J.Identifier) assignment.getVariable();
                            if (!"query".equals(key.getSimpleName())) {
                                continue;
                            }
                            String value = literalValue(assignment.getAssignment());
                            if (value == null) {
                                return false;
                            }
                            String trimmed = value.trim().toLowerCase(Locale.ROOT);
                            return trimmed.startsWith("delete") || trimmed.startsWith("update") || trimmed.startsWith("insert");
                        }
                        return false;
                    }

                    private String literalValue(Expression expr) {
                        if (!(expr instanceof J.Literal)) {
                            return null;
                        }
                        J.Literal literal = (J.Literal) expr;
                        return literal.getValue() instanceof String ? (String) literal.getValue() : null;
                    }

                    private List<J.Annotation> extractArrayEntries(J.Annotation container) {
                        if (container.getArguments() == null || container.getArguments().isEmpty()) {
                            return Collections.emptyList();
                        }
                        if (!(container.getArguments().get(0) instanceof J.NewArray)) {
                            return Collections.emptyList();
                        }
                        J.NewArray newArray = (J.NewArray) container.getArguments().get(0);
                        if (newArray.getInitializer() == null) {
                            return Collections.emptyList();
                        }
                        List<J.Annotation> entries = new ArrayList<>();
                        for (Expression elem : newArray.getInitializer()) {
                            if (elem instanceof J.Annotation) {
                                entries.add((J.Annotation) elem);
                            }
                        }
                        return entries;
                    }

                    private J.Annotation renameAnnotationType(J.Annotation a, String sourceFqn, String targetFqn) {
                        String simpleName = targetFqn.substring(targetFqn.lastIndexOf('.') + 1);
                        JavaType.Class targetType = JavaType.ShallowClass.build(targetFqn);
                        J.Identifier newName = ((J.Identifier) a.getAnnotationType())
                                .withSimpleName(simpleName)
                                .withType(targetType);
                        maybeRemoveImport(sourceFqn);
                        maybeAddImport(targetFqn);
                        return a.withAnnotationType(newName).withType(targetType);
                    }

                    private J.Annotation renameQueryAttribute(J.Annotation a) {
                        if (a.getArguments() == null) {
                            return a;
                        }
                        return a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                            if (!(arg instanceof J.Assignment)) {
                                return arg;
                            }
                            J.Assignment assignment = (J.Assignment) arg;
                            if (!(assignment.getVariable() instanceof J.Identifier)) {
                                return arg;
                            }
                            J.Identifier key = (J.Identifier) assignment.getVariable();
                            if (!"query".equals(key.getSimpleName())) {
                                return arg;
                            }
                            return assignment
                                    .withVariable(key.withSimpleName("statement"))
                                    .withPrefix(Space.SINGLE_SPACE);
                        }));
                    }

                    private J.Annotation rebuildContainer(J.Annotation original, List<J.Annotation> keepEntries) {
                        J.NewArray newArray = (J.NewArray) original.getArguments().get(0);
                        JContainer<Expression> container = newArray.getPadding().getInitializer();
                        List<JRightPadded<Expression>> originalElements = container.getPadding().getElements();

                        Space entryIndent = originalElements.isEmpty()
                                ? Space.build("\n    ", Collections.emptyList())
                                : originalElements.get(0).getElement().getPrefix();

                        List<JRightPadded<Expression>> filtered = ListUtils.map(originalElements, rp -> {
                            if (rp.getElement() instanceof J.Annotation && keepEntries.contains(rp.getElement())) {
                                return rp.withElement(rp.getElement().withPrefix(entryIndent));
                            }
                            return null;
                        });

                        List<JRightPadded<Expression>> newPadded = ListUtils.mapLast(filtered,
                                last -> last.withAfter(Space.build("\n", Collections.emptyList())));

                        JContainer<Expression> newContainer = container.getPadding().withElements(newPadded);
                        J.NewArray rebuilt = newArray.getPadding().withInitializer(newContainer);
                        return original.withArguments(ListUtils.mapFirst(original.getArguments(), __ -> rebuilt));
                    }
                });
    }
}
