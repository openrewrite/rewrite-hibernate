/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.hibernate.hibernate60;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class MigrateHypersistenceUtils61Types extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate `io.hypersistence:hypersistence-utils-hibernate` Json type";
    }

    @Override
    public String getDescription() {
        return "When `io.hypersistence.utils` are being used, " +
                "removes `@org.hibernate.annotations.TypeDefs` annotation as it doesn't exist in Hibernate 6 and updates generic JSON type mapping.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>("org.hibernate.annotations.Type", true),
                        Preconditions.or(
                                new UsesType<>("com.vladmihalcea..*", true),
                                new UsesType<>("io.hypersistence.utils..*", true))),
                new MigrateHibernateAnnotationType());
    }

    private static class MigrateHibernateAnnotationType extends JavaIsoVisitor<ExecutionContext> {
        private static final String HIBERNATE_ANNOTATIONS_TYPE_DEF_FULLNAME = "org.hibernate.annotations.TypeDef";
        private static final String HIBERNATE_ANNOTATIONS_TYPE_DEFS_FULLNAME = "org.hibernate.annotations.TypeDefs";
        private static final String TYPE_DEFS_ANNOTATION = "TypeDefs";
        private static final String HYPERSISTENCE_UTILS_JSON_TYPE_FULLNAME = "io.hypersistence.utils.hibernate.type.json.JsonType";
        private static final String HYPERSISTENCE_UTILS_JSON_BINARY_TYPE_FULLNAME = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType";
        private static final String HYPERSISTENCE_UTILS_JSON_STRING_TYPE_FULLNAME = "io.hypersistence.utils.hibernate.type.json.JsonStringType";
        private static final String JSON_TYPE_CLASS = "JsonType.class";

        private static boolean isHibernateTypeAnnotation(J.Annotation annotation) {
            return annotation.getAnnotationType() instanceof J.Identifier
                    && "Type".equals(((J.Identifier) annotation.getAnnotationType()).getSimpleName())
                    && annotation.getArguments() != null;
        }

        private static boolean isTypeJsonArgument(Expression arg) {
            return arg instanceof J.Assignment
                    && ((J.Assignment) arg).getVariable() instanceof J.Identifier
                    && ((J.Assignment) arg).getAssignment() instanceof J.Literal
                    && "type".equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName())
                    && ((J.Literal) ((J.Assignment) arg).getAssignment()).getValue() != null
                    && ((J.Literal) ((J.Assignment) arg).getAssignment()).getValue().toString().contains("json");
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

            maybeRemoveImport(HIBERNATE_ANNOTATIONS_TYPE_DEF_FULLNAME);
            maybeRemoveImport(HIBERNATE_ANNOTATIONS_TYPE_DEFS_FULLNAME);

            List<J.Annotation> newLeadingAnnotations = classDecl.getLeadingAnnotations().stream()
                    .filter(annotation -> !((J.Identifier) annotation.getAnnotationType()).getSimpleName().equals(TYPE_DEFS_ANNOTATION))
                    .collect(Collectors.toList());

            return c.withLeadingAnnotations(newLeadingAnnotations);
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            // check if annotation is @org.hibernate.annotations.Type
            if (isHibernateTypeAnnotation(annotation)) {
                List<Expression> arguments = annotation.getArguments();
                J.Annotation newAnnotation = annotation.withArguments(arguments.stream()
                        .map(arg -> {
                            if (isTypeJsonArgument(arg)) {
                                // import is not added when onlyIfReferenced is true because it's only used in annotation and there is no such field
                                maybeAddImport(HYPERSISTENCE_UTILS_JSON_TYPE_FULLNAME, false);

                                return ((J.Assignment) arg)
                                        .withVariable(((J.Identifier) ((J.Assignment) arg).getVariable()).withSimpleName("value"))
                                        .withAssignment(((J.Literal) ((J.Assignment) arg).getAssignment())
                                                .withType(JavaType.buildType(HYPERSISTENCE_UTILS_JSON_TYPE_FULLNAME))
                                                .withValue(JSON_TYPE_CLASS)
                                                .withValueSource(JSON_TYPE_CLASS));
                            } else {
                                return arg;
                            }
                        })
                        .collect(Collectors.toList()));

                maybeRemoveImport(HYPERSISTENCE_UTILS_JSON_BINARY_TYPE_FULLNAME);
                maybeRemoveImport(HYPERSISTENCE_UTILS_JSON_STRING_TYPE_FULLNAME);
                return newAnnotation;
            }

            return super.visitAnnotation(annotation, ctx);
        }
    }
}
