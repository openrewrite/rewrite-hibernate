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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindImplementations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
public class EmptyInterceptorToInterface extends Recipe {

    private final String EMPTY_INTERCEPTOR = "org.hibernate.EmptyInterceptor";
    private final String INTERCEPTOR = "org.hibernate.Interceptor";
    private final String STATEMENT_INSPECTOR = "org.hibernate.resource.jdbc.spi.StatementInspector";
    private static final AnnotationMatcher OVERRIDE_ANNOTATION_MATCHER = new AnnotationMatcher("java.lang.Override");

    @Override
    public String getDisplayName() {
        return "Replace `extends EmptyInterceptor` with `implements Interceptor` and potentially `StatementInspector`";
    }

    @Override
    public String getDescription() {
        return "In Hibernate 6.0 the `Interceptor` interface received default implementations therefore the NOOP implementation that could be extended was no longer needed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindImplementations(EMPTY_INTERCEPTOR),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (cd.getExtends() != null && TypeUtils.isOfClassType(cd.getExtends().getType(), EMPTY_INTERCEPTOR)) {
                            cd = cd.withExtends(null).withImplements(ListUtils.concat(cd.getImplements(), (TypeTree) TypeTree.build("Interceptor").withType(JavaType.buildType(INTERCEPTOR)).withPrefix(Space.SINGLE_SPACE)));
                            Boolean prepareStatement = getCursor().pollMessage("prepareStatementFound");
                            if (Boolean.TRUE.equals(prepareStatement)) {
                                cd = cd.withImplements(ListUtils.concat(cd.getImplements(), (TypeTree) TypeTree.build("StatementInspector").withType(JavaType.buildType(STATEMENT_INSPECTOR)).withPrefix(Space.SINGLE_SPACE)));
                            }
                            maybeAddImport(INTERCEPTOR);
                            maybeAddImport(STATEMENT_INSPECTOR);
                            maybeRemoveImport(EMPTY_INTERCEPTOR);
                        }
                        return autoFormat(cd, ctx);
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        if (md.getLeadingAnnotations().stream().anyMatch(OVERRIDE_ANNOTATION_MATCHER::matches) && md.getSimpleName().equals("onPrepareStatement")) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "prepareStatementFound", true);
                            String template = "@Override\n" +
                                              "public String inspect() {\n" +
                                              "}\n";
                            J.MethodDeclaration inspect = JavaTemplate.builder(template)
                                    .javaParser(JavaParser.fromJavaVersion())
                                    .build()
                                    .apply(getCursor(), md.getCoordinates().replace());
                            md = inspect.withBody(md.getBody()).withModifiers(md.getModifiers()).withLeadingAnnotations(md.getLeadingAnnotations()).withParameters(md.getParameters());
                        }
                        return md;
                    }
                }
        );
    }
}
