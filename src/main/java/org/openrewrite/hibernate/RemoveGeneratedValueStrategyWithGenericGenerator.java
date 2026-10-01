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
package org.openrewrite.hibernate;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.trait.AttributeValue;
import org.openrewrite.java.trait.Literal;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemoveGeneratedValueStrategyWithGenericGenerator extends Recipe {

    private static final String GENERATED_VALUE = "jakarta.persistence.GeneratedValue";
    private static final String GENERIC_GENERATOR = "org.hibernate.annotations.GenericGenerator";
    private static final String TABLE_GENERATOR = "jakarta.persistence.TableGenerator";
    private static final String SEQUENCE_GENERATOR = "jakarta.persistence.SequenceGenerator";
    private static final String GENERATION_TYPE = "jakarta.persistence.GenerationType";

    @Getter
    final String displayName = "Remove `@GeneratedValue` strategy when a custom `@GenericGenerator` is used";

    @Getter
    final String description = "Hibernate 7 rejects `@GeneratedValue(strategy = TABLE|SEQUENCE, generator = \"X\")` " +
            "when `\"X\"` resolves to a `@GenericGenerator` rather than a `@TableGenerator`/`@SequenceGenerator`, and falls back to a " +
            "plain JPA generator that looks for a table named `\"X\"`. Dropping the `strategy` attribute keeps the custom " +
            "generator intact and remains compatible with Hibernate 5.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(GENERATED_VALUE, false),
                        new UsesType<>(GENERIC_GENERATOR, false)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                        getCursor().putMessage("generatorsByName", collectGenerators(cd));
                        return super.visitClassDeclaration(cd, ctx);
                    }

                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        if (!TypeUtils.isOfClassType(a.getType(), GENERATED_VALUE)) {
                            return a;
                        }

                        Annotated gv = new Annotated(getCursor());
                        AttributeValue strategy = gv.getAttributeValue("strategy").orElse(null);
                        String generatorName = gv.getAttribute("generator").map(Literal::getString).orElse(null);
                        if (strategy == null || generatorName == null) {
                            return a;
                        }
                        if (!strategy.isEnumConstant(GENERATION_TYPE, "TABLE") &&
                                !strategy.isEnumConstant(GENERATION_TYPE, "SEQUENCE")) {
                            return a;
                        }

                        Map<String, GeneratorKind> generators = getCursor().getNearestMessage("generatorsByName");
                        if (generators == null || generators.get(generatorName) != GeneratorKind.GENERIC) {
                            return a;
                        }

                        J.Assignment strategyArg = (J.Assignment) strategy.getCursor().getParentTreeCursor().getValue();
                        List<Expression> remaining = ListUtils.map(a.getArguments(), arg -> arg == strategyArg ? null : arg);
                        remaining = ListUtils.mapFirst(remaining, first -> first.withPrefix(Space.EMPTY));
                        maybeRemoveImport(GENERATION_TYPE);
                        return a.withArguments(remaining);
                    }
                });
    }

    private enum GeneratorKind {GENERIC, CONFLICTING}

    private static Map<String, GeneratorKind> collectGenerators(J.ClassDeclaration cd) {
        Map<String, GeneratorKind> result = new HashMap<>();
        recordGenerators(cd.getLeadingAnnotations(), result);
        for (Statement stmt : cd.getBody().getStatements()) {
            if (stmt instanceof J.VariableDeclarations) {
                recordGenerators(((J.VariableDeclarations) stmt).getLeadingAnnotations(), result);
            }
        }
        return result;
    }

    private static void recordGenerators(List<J.Annotation> annotations, Map<String, GeneratorKind> out) {
        for (J.Annotation ann : annotations) {
            boolean isGeneric = TypeUtils.isOfClassType(ann.getType(), GENERIC_GENERATOR);
            boolean isConflicting = TypeUtils.isOfClassType(ann.getType(), TABLE_GENERATOR) ||
                    TypeUtils.isOfClassType(ann.getType(), SEQUENCE_GENERATOR);
            if (!isGeneric && !isConflicting) {
                continue;
            }
            String name = annotationNameAttribute(ann);
            if (name == null) {
                continue;
            }
            if (isConflicting) {
                out.put(name, GeneratorKind.CONFLICTING);
            } else {
                out.putIfAbsent(name, GeneratorKind.GENERIC);
            }
        }
    }

    private static String annotationNameAttribute(J.Annotation ann) {
        List<Expression> args = ann.getArguments();
        if (args == null) {
            return null;
        }
        for (Expression arg : args) {
            if (arg instanceof J.Assignment) {
                J.Assignment assign = (J.Assignment) arg;
                if (assign.getVariable() instanceof J.Identifier &&
                        "name".equals(((J.Identifier) assign.getVariable()).getSimpleName()) &&
                        assign.getAssignment() instanceof J.Literal) {
                    Object v = ((J.Literal) assign.getAssignment()).getValue();
                    return v instanceof String ? (String) v : null;
                }
            }
        }
        return null;
    }
}
