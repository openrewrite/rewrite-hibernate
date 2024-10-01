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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MigrateUserType extends Recipe {

    private static final String USER_TYPE = "org.hibernate.usertype.UserType";
    private static final MethodMatcher RETURNED_CLASS = new MethodMatcher("* returnedClass()");
    private static final MethodMatcher EQUALS = new MethodMatcher("* equals(java.lang.Object, java.lang.Object)");
    private static final MethodMatcher HASHCODE = new MethodMatcher("* hashCode(java.lang.Object)");
    private static final MethodMatcher NULL_SAFE_GET_RESULT_SET = new MethodMatcher("* hashcode(java.lang.Object)");
    private static final MethodMatcher NULL_SAFE_GET_PREPARED_STATEMENT = new MethodMatcher("* hashcode(java.lang.Object)");
    private static final MethodMatcher DEEP_COPY = new MethodMatcher("* deepCopy(java.lang.Object)");
    private static final MethodMatcher DISASSEMBLE = new MethodMatcher("* disassemble(java.lang.Object)");
    private static final MethodMatcher ASSEMBLE = new MethodMatcher("* assemble(java.io.Serializable, java.lang.Object)");
    private static final MethodMatcher REPLACE = new MethodMatcher("* replace(java.lang.Object, java.lang.Object, java.lang.Object)");
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
                                return TypeTree.build("UserType<" + parameterizedType.getTarget() + ">").withType(JavaType.buildType(USER_TYPE)).withPrefix(Space.SINGLE_SPACE);
                            }
                            return impl;
                        }));
                        updateCursor(cd);
                        return super.visitClassDeclaration(cd, ctx);
                    }


                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                        J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (cd != null) {
                            if (RETURNED_CLASS.matches(md, cd)) {
                                md = md.withReturnTypeExpression(TypeTree.build("Class<" + parameterizedType.getTarget() + ">").withPrefix(md.getReturnTypeExpression().getPrefix()));
                            }
                            if (EQUALS.matches(md, cd)) {
                                md = changeParameterTypes(md, Arrays.asList("x", "y"));
                            }
                            if (HASHCODE.matches(md, cd)) {
                                md = changeParameterTypes(md, Collections.singletonList("x"));
                            }
                            if (NULL_SAFE_GET_RESULT_SET.matches(md, cd)) {
                            }
                            if (NULL_SAFE_GET_PREPARED_STATEMENT.matches(md, cd)) {
                            }
                            if (DEEP_COPY.matches(md, cd)) {
                                md = md.withReturnTypeExpression(parameterizedType.getTarget().withPrefix(Space.SINGLE_SPACE)).withPrefix(md.getReturnTypeExpression().getPrefix());
                                md = changeParameterTypes(md, Collections.singletonList("value"));
                            }
                            if (DISASSEMBLE.matches(md, cd)) {
                                md = changeParameterTypes(md, Collections.singletonList("value"));
                            }
                            if (ASSEMBLE.matches(md, cd)) {
                                md = md.withReturnTypeExpression(parameterizedType.getTarget().withPrefix(Space.SINGLE_SPACE)).withPrefix(md.getReturnTypeExpression().getPrefix());
                            }
                            if (REPLACE.matches(md, cd)) {
                                md = md.withReturnTypeExpression(parameterizedType.getTarget().withPrefix(Space.SINGLE_SPACE)).withPrefix(md.getReturnTypeExpression().getPrefix());
                                md = changeParameterTypes(md, Arrays.asList("original", "target"));
                            }
                        }
                        return maybeAutoFormat(method, md, ctx);
                    }

                    private J.MethodDeclaration changeParameterTypes(J.MethodDeclaration md, List<String> paramNames){
                        JavaType.Method met = md.getMethodType().withParameterTypes(ListUtils.map(md.getMethodType().getParameterTypes(),
                                (index, type) -> {
                                    if (paramNames.contains(md.getMethodType().getParameterNames().get(index))) {
                                        type = TypeUtils.isOfType(JavaType.buildType("java.lang.Object"), type) ? parameterizedType.getTarget().getType() : type;
                                    }
                                    return type;
                                }));
                        return md.withParameters(ListUtils.map(md.getParameters(), param -> {
                            if (param instanceof J.VariableDeclarations) {
                                param = ((J.VariableDeclarations) param).withType(parameterizedType.getTarget().getType()).withTypeExpression((TypeTree) parameterizedType.getTarget());
                                param = ((J.VariableDeclarations) param).withVariables(ListUtils.map(((J.VariableDeclarations) param).getVariables(), var -> {
                                    if (paramNames.contains(var.getSimpleName())) {
                                        var = var.withType(parameterizedType.getTarget().getType()).withVariableType(var.getVariableType().withType(parameterizedType.getTarget().getType()).withOwner(met));
                                    }
                                    return var;
                                }));
                            }
                            return param;
                        }));
                    }
                });
    }
}
