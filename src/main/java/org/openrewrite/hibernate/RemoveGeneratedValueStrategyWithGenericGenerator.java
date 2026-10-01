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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

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
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        if (!TypeUtils.isOfClassType(a.getType(), GENERATED_VALUE)) {
                            return a;
                        }
                        List<Expression> args = a.getArguments();
                        if (args == null || args.size() < 2) {
                            return a;
                        }

                        J.Assignment strategyArg = null;
                        String strategyValue = null;
                        String generatorName = null;
                        for (Expression arg : args) {
                            if (!(arg instanceof J.Assignment)) {
                                continue;
                            }
                            J.Assignment assign = (J.Assignment) arg;
                            if (!(assign.getVariable() instanceof J.Identifier)) {
                                continue;
                            }
                            String key = ((J.Identifier) assign.getVariable()).getSimpleName();
                            if ("strategy".equals(key)) {
                                strategyArg = assign;
                                strategyValue = enumConstantName(assign.getAssignment());
                            } else if ("generator".equals(key)) {
                                generatorName = stringLiteralValue(assign.getAssignment());
                            }
                        }

                        if (strategyArg == null || generatorName == null) {
                            return a;
                        }
                        if (!"TABLE".equals(strategyValue) && !"SEQUENCE".equals(strategyValue)) {
                            return a;
                        }

                        J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (enclosingClass == null) {
                            return a;
                        }

                        GeneratorKind kind = findGeneratorKind(enclosingClass, generatorName);
                        if (kind != GeneratorKind.GENERIC) {
                            return a;
                        }

                        maybeRemoveImport(GENERATION_TYPE);
                        return JavaTemplate.builder("@GeneratedValue(generator = \"" + escape(generatorName) + "\")")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.persistence-api"))
                                .imports(GENERATED_VALUE)
                                .build()
                                .apply(getCursor(), a.getCoordinates().replace());
                    }
                });
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static @Nullable String enumConstantName(Expression expr) {
        if (expr instanceof J.FieldAccess) {
            return ((J.FieldAccess) expr).getSimpleName();
        }
        if (expr instanceof J.Identifier) {
            return ((J.Identifier) expr).getSimpleName();
        }
        return null;
    }

    private static @Nullable String stringLiteralValue(Expression expr) {
        if (expr instanceof J.Literal) {
            Object v = ((J.Literal) expr).getValue();
            return v instanceof String ? (String) v : null;
        }
        return null;
    }

    private enum GeneratorKind {GENERIC, TABLE, SEQUENCE, NONE}

    private static GeneratorKind findGeneratorKind(J.ClassDeclaration cls, String name) {
        GeneratorKind found = GeneratorKind.NONE;
        for (J.Annotation ann : cls.getLeadingAnnotations()) {
            GeneratorKind k = classifyIfNamed(ann, name);
            if (k == GeneratorKind.TABLE || k == GeneratorKind.SEQUENCE) {
                return k;
            }
            if (k == GeneratorKind.GENERIC) {
                found = GeneratorKind.GENERIC;
            }
        }
        for (org.openrewrite.java.tree.Statement stmt : cls.getBody().getStatements()) {
            if (!(stmt instanceof J.VariableDeclarations)) {
                continue;
            }
            for (J.Annotation ann : ((J.VariableDeclarations) stmt).getLeadingAnnotations()) {
                GeneratorKind k = classifyIfNamed(ann, name);
                if (k == GeneratorKind.TABLE || k == GeneratorKind.SEQUENCE) {
                    return k;
                }
                if (k == GeneratorKind.GENERIC) {
                    found = GeneratorKind.GENERIC;
                }
            }
        }
        return found;
    }

    private static GeneratorKind classifyIfNamed(J.Annotation ann, String name) {
        String fqn = annotationFqn(ann);
        if (fqn == null) {
            return GeneratorKind.NONE;
        }
        boolean isGeneric = GENERIC_GENERATOR.equals(fqn);
        boolean isTable = TABLE_GENERATOR.equals(fqn);
        boolean isSequence = SEQUENCE_GENERATOR.equals(fqn);
        if (!isGeneric && !isTable && !isSequence) {
            return GeneratorKind.NONE;
        }
        if (!name.equals(nameAttribute(ann))) {
            return GeneratorKind.NONE;
        }
        return isGeneric ? GeneratorKind.GENERIC : isTable ? GeneratorKind.TABLE : GeneratorKind.SEQUENCE;
    }

    private static @Nullable String annotationFqn(J.Annotation ann) {
        return ann.getType() instanceof org.openrewrite.java.tree.JavaType.FullyQualified ?
                ((org.openrewrite.java.tree.JavaType.FullyQualified) ann.getType()).getFullyQualifiedName() :
                null;
    }

    private static @Nullable String nameAttribute(J.Annotation ann) {
        List<Expression> args = ann.getArguments();
        if (args == null) {
            return null;
        }
        for (Expression arg : args) {
            if (!(arg instanceof J.Assignment)) {
                continue;
            }
            J.Assignment assign = (J.Assignment) arg;
            if (assign.getVariable() instanceof J.Identifier &&
                    "name".equals(((J.Identifier) assign.getVariable()).getSimpleName())) {
                return stringLiteralValue(assign.getAssignment());
            }
        }
        return null;
    }
}
