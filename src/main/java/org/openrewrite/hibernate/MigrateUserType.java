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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Optional;

public class MigrateUserType extends Recipe {

    private static final String USER_TYPE = "org.hibernate.usertype.UserType";
    private static J.FieldAccess parameterizedType = null;

    @Override
    public String getDisplayName() {
        return "Migrate UserType to hibernate 6";
    }

    @Override
    public String getDescription() {
        return "With hibernate 6 the UserType interface received a type parameter making it more strictly typed" +
               "This recipe applies the changes required to adhere to this change.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(USER_TYPE, false),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = classDecl;
                        Optional<J.MethodDeclaration> returnedClass = cd.getBody().getStatements().stream().filter(J.MethodDeclaration.class::isInstance).map(J.MethodDeclaration.class::cast).filter(stmt -> stmt.getSimpleName().equals("returnedClass")).findFirst();
                        returnedClass.ifPresent(retClass -> parameterizedType = (J.FieldAccess) retClass.getBody().getStatements().stream().filter(J.Return.class::isInstance).map(J.Return.class::cast).map(J.Return::getExpression).findFirst().get());

                        cd = cd.withImplements(ListUtils.map(cd.getImplements(), impl -> {
                            if (TypeUtils.isAssignableTo(USER_TYPE, impl.getType())) {
                                System.out.println(impl);
                                return TypeTree.build("UserType<" + parameterizedType.getTarget() + ">").withType(JavaType.buildType(USER_TYPE));
                            }
                            return impl;
                        }));
                        updateCursor(cd);
                        return super.visitClassDeclaration(cd, ctx);
                    }


                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = method;
                        super.visitMethodDeclaration(method, ctx);
                        if (md.getSimpleName().equals("returnedClass")) {
                            md = md.withReturnTypeExpression(TypeTree.build("Class<" + parameterizedType.getTarget() + ">"));
                        }
                        return md;
                    }
                });
    }
}
