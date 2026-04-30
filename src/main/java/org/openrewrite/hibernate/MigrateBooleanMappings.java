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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MigrateBooleanMappings extends Recipe {

    private static final Map<String, String> REPLACEMENTS = new HashMap<>();

    static {
        REPLACEMENTS.put("org.hibernate.type.TrueFalseBooleanType", "TrueFalseConverter");
        REPLACEMENTS.put("true_false", "TrueFalseConverter");
        REPLACEMENTS.put("org.hibernate.type.YesNoBooleanType", "YesNoConverter");
        REPLACEMENTS.put("yes_no", "YesNoConverter");
        REPLACEMENTS.put("org.hibernate.type.NumericBooleanType", "NumericBooleanConverter");
        REPLACEMENTS.put("numeric_boolean", "NumericBooleanConverter");
    }

    private static final Map<String, String> CLASS_REPLACEMENTS = new HashMap<>();

    static {
        CLASS_REPLACEMENTS.put("org.hibernate.type.TrueFalseType", "TrueFalseConverter");
        CLASS_REPLACEMENTS.put("org.hibernate.type.YesNoType", "YesNoConverter");
        CLASS_REPLACEMENTS.put("org.hibernate.type.NumericBooleanType", "NumericBooleanConverter");
    }

    @Getter
    final String displayName = "Replace boolean type mappings with converters";

    @Getter
    final String description = "Replaces type mapping of booleans with appropriate attribute converters.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.hibernate.annotations.Type", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation ann = super.visitAnnotation(annotation, ctx);
                        if (!TypeUtils.isOfClassType(ann.getType(), "org.hibernate.annotations.Type")) {
                            return ann;
                        }

                        List<Expression> args = ann.getArguments();
                        if (args == null) {
                            return ann;
                        }

                        Object type = args.stream()
                                .filter(exp -> {
                                    if (exp instanceof J.Assignment) {
                                        J.Identifier variable = (J.Identifier) ((J.Assignment) exp).getVariable();
                                        return "type".equals(variable.getSimpleName());
                                    }
                                    return false;
                                })
                                .findFirst()
                                .map(exp -> {
                                    Expression value = ((J.Assignment) exp).getAssignment();
                                    if (value instanceof J.Literal) {
                                        return ((J.Literal) value).getValue();
                                    }
                                    return null;
                                })
                                .orElse(null);

                        if (type instanceof String && REPLACEMENTS.containsKey((String) type)) {
                            String converterName = REPLACEMENTS.get((String) type);
                            String converterFQN = String.format("org.hibernate.type.%s", converterName);

                            ann = JavaTemplate.builder(String.format("@Convert(converter = %s.class)", converterName))
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hibernate-core-6+", "jakarta.persistence-api"))
                                    .imports(converterFQN, "jakarta.persistence.Convert")
                                    .contextSensitive()
                                    .build().apply(getCursor(), ann.getCoordinates().replace());

                            maybeRemoveImport("org.hibernate.annotations.Type");
                            maybeAddImport("jakarta.persistence.Convert");
                            maybeAddImport(converterFQN);
                            return ann;
                        }

                        String classFQN = getClassArgumentFQN(args);
                        if (classFQN != null && CLASS_REPLACEMENTS.containsKey(classFQN)) {
                            String converterName = CLASS_REPLACEMENTS.get(classFQN);
                            String converterFQN = String.format("org.hibernate.type.%s", converterName);

                            ann = JavaTemplate.builder(String.format("@Convert(converter = %s.class)", converterName))
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hibernate-core-6+", "jakarta.persistence-api"))
                                    .imports(converterFQN, "jakarta.persistence.Convert")
                                    .contextSensitive()
                                    .build().apply(getCursor(), ann.getCoordinates().replace());

                            maybeRemoveImport("org.hibernate.annotations.Type");
                            maybeRemoveImport(classFQN);
                            maybeAddImport("jakarta.persistence.Convert");
                            maybeAddImport(converterFQN);
                        }

                        return ann;
                    }

                    private @Nullable String getClassArgumentFQN(List<Expression> args) {
                        if (args.size() != 1) {
                            return null;
                        }
                        Expression arg = args.get(0);
                        Expression classExpr;
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            if (!(assignment.getVariable() instanceof J.Identifier) ||
                                    !"value".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                                return null;
                            }
                            classExpr = assignment.getAssignment();
                        } else {
                            classExpr = arg;
                        }
                        if (!(classExpr instanceof J.FieldAccess)) {
                            return null;
                        }
                        J.FieldAccess fa = (J.FieldAccess) classExpr;
                        if (!"class".equals(fa.getName().getSimpleName())) {
                            return null;
                        }
                        JavaType.FullyQualified fqn = TypeUtils.asFullyQualified(fa.getTarget().getType());
                        return fqn == null ? null : fqn.getFullyQualifiedName();
                    }
                }
        );
    }
}
