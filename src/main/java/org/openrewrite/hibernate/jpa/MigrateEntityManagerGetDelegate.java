// SPDX-License-Identifier: Apache-2.0
// Copyright Red Hat Inc. and Hibernate Authors
package org.openrewrite.hibernate.jpa;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

/**
 * Replaces calls to {@code EntityManager.getDelegate()} with
 * {@code EntityManager.unwrap(Object.class)}.
 *
 * <p>{@code getDelegate()} was deprecated in Jakarta Persistence 4.0 in favour of
 * the type-safe {@code unwrap(Class)} method. Both return the underlying provider
 * object; using {@code Object.class} preserves the original untyped return type.
 *
 * <pre>
 * // before
 * Object delegate = em.getDelegate();
 *
 * // after
 * Object delegate = em.unwrap(Object.class);
 * </pre>
 */
public class MigrateEntityManagerGetDelegate extends Recipe {

    private static final MethodMatcher GET_DELEGATE =
            new MethodMatcher("jakarta.persistence.EntityManager getDelegate()");

    @Override
    public String getDisplayName() {
        return "Replace EntityManager.getDelegate() with unwrap(Object.class)";
    }

    @Override
    public String getDescription() {
        return "Replaces EntityManager.getDelegate(), deprecated in Jakarta Persistence 4.0, " +
                "with the equivalent EntityManager.unwrap(Object.class).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(GET_DELEGATE),
                new JavaIsoVisitor<ExecutionContext>() {
                    private final JavaTemplate unwrapTemplate = JavaTemplate
                            .builder("#{any(jakarta.persistence.EntityManager)}.unwrap(Object.class)")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpath("jakarta.persistence-api"))
                            .build();

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                    ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (!GET_DELEGATE.matches(m)) {
                            return m;
                        }
                        return unwrapTemplate.apply(getCursor(), m.getCoordinates().replace(), m.getSelect());
                    }
                });
    }
}
