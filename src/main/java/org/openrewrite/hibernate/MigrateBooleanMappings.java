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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
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

    @Override
    public String getDisplayName() {
        return "Replace boolean type mappings with converters";
    }

    @Override
    public String getDescription() {
        return "Replaces type mapping of booleans with appropriate attribute converters.";
    }

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
                                    .javaParser(JavaParser.fromJavaVersion().classpath("hibernate-core", "jakarta.persistence-api"))
                                    .imports(converterFQN, "jakarta.persistence.Convert")
                                    .contextSensitive()
                                    .build().apply(getCursor(), ann.getCoordinates().replace());

                            maybeAddImport("jakarta.persistence.Convert");
                            maybeAddImport(converterFQN);
                            maybeRemoveImport("org.hibernate.annotations.Type");
                        }

                        return ann;
                    }
                }
        );
    }
}
