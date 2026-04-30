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
        // true/false boolean
        REPLACEMENTS.put("true_false", "TrueFalseConverter");
        REPLACEMENTS.put("org.hibernate.type.TrueFalseType", "TrueFalseConverter");
        REPLACEMENTS.put("org.hibernate.type.TrueFalseBooleanType", "TrueFalseConverter");
        // yes/no boolean
        REPLACEMENTS.put("yes_no", "YesNoConverter");
        REPLACEMENTS.put("org.hibernate.type.YesNoType", "YesNoConverter");
        REPLACEMENTS.put("org.hibernate.type.YesNoBooleanType", "YesNoConverter");
        // numeric boolean
        REPLACEMENTS.put("numeric_boolean", "NumericBooleanConverter");
        REPLACEMENTS.put("org.hibernate.type.NumericBooleanType", "NumericBooleanConverter");
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

                        String converterName = null;
                        String legacyClassFQN = null;
                        for (Expression arg : args) {
                            String key;
                            if (arg instanceof J.Assignment) {
                                J.Assignment a = (J.Assignment) arg;
                                if (!(a.getVariable() instanceof J.Identifier)) {
                                    continue;
                                }
                                String attr = ((J.Identifier) a.getVariable()).getSimpleName();
                                if ("type".equals(attr) && a.getAssignment() instanceof J.Literal) {
                                    Object v = ((J.Literal) a.getAssignment()).getValue();
                                    key = v instanceof String ? (String) v : null;
                                } else if ("value".equals(attr)) {
                                    legacyClassFQN = classRefFQN(a.getAssignment());
                                    key = legacyClassFQN;
                                } else {
                                    continue;
                                }
                            } else if (args.size() == 1) {
                                legacyClassFQN = classRefFQN(arg);
                                key = legacyClassFQN;
                            } else {
                                continue;
                            }
                            if (key != null) {
                                converterName = REPLACEMENTS.get(key);
                                if (converterName != null) {
                                    break;
                                }
                            }
                        }
                        if (converterName == null) {
                            return ann;
                        }

                        String converterFQN = "org.hibernate.type." + converterName;
                        ann = JavaTemplate.builder("@Convert(converter = " + converterName + ".class)")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hibernate-core-6+", "jakarta.persistence-api"))
                                .imports(converterFQN, "jakarta.persistence.Convert")
                                .contextSensitive()
                                .build().apply(getCursor(), ann.getCoordinates().replace());

                        maybeRemoveImport("org.hibernate.annotations.Type");
                        if (legacyClassFQN != null) {
                            maybeRemoveImport(legacyClassFQN);
                        }
                        maybeAddImport("jakarta.persistence.Convert");
                        maybeAddImport(converterFQN);
                        return ann;
                    }

                    private @Nullable String classRefFQN(Expression expr) {
                        if (!(expr instanceof J.FieldAccess)) {
                            return null;
                        }
                        J.FieldAccess fa = (J.FieldAccess) expr;
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
