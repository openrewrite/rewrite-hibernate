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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindImplementations;
import org.openrewrite.java.tree.*;

public class EmptyInterceptorToInterface extends Recipe {

    private final String EMPTY_INTERCEPTOR = "org.hibernate.EmptyInterceptor";
    private final String INTERCEPTOR = "org.hibernate.Interceptor";
    private final String STATEMENT_INSPECTOR = "org.hibernate.resource.jdbc.spi.StatementInspector";
    private static final MethodMatcher ON_PREPARE_STATEMENT = new MethodMatcher("org.hibernate.Interceptor onPrepareStatement(java.lang.String)", true);

    @Override
    public String getDisplayName() {
        return "Replace `extends EmptyInterceptor` with `implements Interceptor` and potentially `StatementInspector`";
    }

    @Override
    public String getDescription() {
        return "In Hibernate 6.0 the `Interceptor` interface received default implementations therefore the NOOP implementation that could be extended was no longer needed. " +
               "This recipe migrates 5.x `Interceptor#onPrepareStatement(String)` to 6.0 `StatementInspector#inspect()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindImplementations(EMPTY_INTERCEPTOR), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (cd.getExtends() != null && TypeUtils.isOfClassType(cd.getExtends().getType(), EMPTY_INTERCEPTOR)) {
                            cd = cd.withExtends(null).withImplements(ListUtils.concat(cd.getImplements(), (TypeTree) TypeTree.build("Interceptor").withType(JavaType.buildType(INTERCEPTOR)).withPrefix(Space.SINGLE_SPACE)));
                            maybeAddImport(INTERCEPTOR);
                            if (getCursor().pollMessage("prepareStatementFound") != null) {
                                cd = cd.withImplements(ListUtils.concat(cd.getImplements(), (TypeTree) TypeTree.build("StatementInspector").withType(JavaType.buildType(STATEMENT_INSPECTOR)).withPrefix(Space.SINGLE_SPACE)));
                                maybeAddImport(STATEMENT_INSPECTOR);
                            }
                            maybeRemoveImport(EMPTY_INTERCEPTOR);
                            if (cd.getPadding().getImplements() != null) {
                                cd = cd.getPadding().withImplements(cd.getPadding().getImplements().withBefore(Space.SINGLE_SPACE));
                            }
                        }
                        return cd;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (cd != null && ON_PREPARE_STATEMENT.matches(md, cd)) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "prepareStatementFound", true);
                            J.MethodDeclaration inspectMD = JavaTemplate.apply(
                                    (md.getLeadingAnnotations().isEmpty() ? "" : "@Override ") +
                                    "public String inspect(String overriddenBelow) { return overriddenBelow; }",
                                    getCursor(), md.getCoordinates().replace());
                            return inspectMD
                                    .withPrefix(md.getPrefix())
                                    .withLeadingAnnotations(md.getLeadingAnnotations())
                                    .withParameters(md.getParameters())
                                    .withBody(md.getBody());
                        }
                        return md;
                    }
                }
        );
    }
}
