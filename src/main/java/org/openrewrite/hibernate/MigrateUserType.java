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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.openrewrite.Tree.randomId;

public class MigrateUserType extends Recipe {

    private static final String USER_TYPE = "org.hibernate.usertype.UserType";
    private static final MethodMatcher ASSEMBLE = new MethodMatcher("* assemble(java.io.Serializable, java.lang.Object)");
    private static final MethodMatcher DEEP_COPY = new MethodMatcher("* deepCopy(java.lang.Object)");
    private static final MethodMatcher DISASSEMBLE = new MethodMatcher("* disassemble(java.lang.Object)");
    private static final MethodMatcher EQUALS = new MethodMatcher("* equals(java.lang.Object, java.lang.Object)");
    private static final MethodMatcher HASHCODE = new MethodMatcher("* hashCode(java.lang.Object)");
    private static final MethodMatcher NULL_SAFE_GET_STRING_ARRAY = new MethodMatcher("* nullSafeGet(java.sql.ResultSet, java.lang.String[], org.hibernate.engine.spi.SharedSessionContractImplementor, java.lang.Object)");
    private static final MethodMatcher NULL_SAFE_SET = new MethodMatcher("* nullSafeSet(java.sql.PreparedStatement, java.lang.Object, int, org.hibernate.engine.spi.SharedSessionContractImplementor)");
    private static final MethodMatcher NULL_SAFE_GET_INT = new MethodMatcher("* nullSafeGet(java.sql.ResultSet, int, org.hibernate.engine.spi.SharedSessionContractImplementor, java.lang.Object)");
    private static final MethodMatcher REPLACE = new MethodMatcher("* replace(java.lang.Object, java.lang.Object, java.lang.Object)");
    private static final MethodMatcher RESULT_SET_STRING_PARAM = new MethodMatcher("java.sql.ResultSet *(java.lang.String)");
    private static final MethodMatcher RETURNED_CLASS = new MethodMatcher("* returnedClass()");
    private static final MethodMatcher SQL_TYPES = new MethodMatcher("* sqlTypes()");

    @Override
    public String getDisplayName() {
        return "Migrate `UserType` to Hibernate 6";
    }

    @Override
    public String getDescription() {
        return "With Hibernate 6 the `UserType` interface received a type parameter making it more strictly typed. " +
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
                        returnedClass.ifPresent(retClass -> {
                            if (retClass.getBody() != null) {
                                //noinspection DataFlowIssue
                                getCursor().putMessage("parameterizedType", retClass.getBody().getStatements().stream().filter(J.Return.class::isInstance).map(J.Return.class::cast).map(J.Return::getExpression).findFirst().orElse(null));

                            }
                        });

                        J.FieldAccess parameterizedType = getCursor().getMessage("parameterizedType");
                        cd = cd.withImplements(ListUtils.map(cd.getImplements(), impl -> {
                            if (TypeUtils.isAssignableTo(USER_TYPE, impl.getType()) && parameterizedType != null) {
                                return TypeTree.build("UserType<" + parameterizedType.getTarget() + ">").withType(JavaType.buildType(USER_TYPE)).withPrefix(Space.SINGLE_SPACE);
                            }
                            return impl;
                        }));
                        updateCursor(cd);
                        if (parameterizedType != null) {
                            getCursor().putMessage("parameterizedType", parameterizedType);
                        }
                        return super.visitClassDeclaration(cd, ctx);
                    }


                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = method;
                        J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        J.FieldAccess parameterizedType = getCursor().getNearestMessage("parameterizedType");
                        if (cd != null) {
                            if (SQL_TYPES.matches(md, cd)) {
                                if (md.getBody() != null) {
                                    Optional<J.Return> ret = md.getBody().getStatements().stream().filter(J.Return.class::isInstance).map(J.Return.class::cast).findFirst();
                                    if (ret.isPresent()) {
                                        if (ret.get().getExpression() instanceof J.NewArray) {
                                            J.NewArray newArray = (J.NewArray) ret.get().getExpression();
                                            if (newArray.getInitializer() != null) {
                                                String template = "@Override\n" +
                                                                  "public int getSqlType() {\n" +
                                                                  "    return #{any()};\n" +
                                                                  "}";
                                                md = JavaTemplate.builder(template)
                                                        .javaParser(JavaParser.fromJavaVersion())
                                                        .build()
                                                        .apply(getCursor(), md.getCoordinates().replace(), newArray.getInitializer().get(0)).withId(md.getId());
                                            }
                                        }

                                    }
                                }
                            }
                            if (RETURNED_CLASS.matches(md, cd) && parameterizedType != null) {
                                md = md.withReturnTypeExpression(TypeTree.build("Class<" + parameterizedType.getTarget() + ">"));
                                if (md.getReturnTypeExpression() != null) {
                                    md = md.withPrefix(md.getReturnTypeExpression().getPrefix());
                                }
                            }
                            if (EQUALS.matches(md, cd)) {
                                md = changeParameterTypes(md, Arrays.asList("x", "y"));
                            }
                            if (HASHCODE.matches(md, cd)) {
                                md = changeParameterTypes(md, Collections.singletonList("x"));
                            }
                            if (NULL_SAFE_GET_STRING_ARRAY.matches(md, cd)) {
                                String template = "@Override\n" +
                                                  "public BigDecimal nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {\n" +
                                                  "}";
                                J.MethodDeclaration updatedParam = JavaTemplate.builder(template)
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hibernate-core"))
                                        .imports("java.math.BigDecimal", "java.sql.ResultSet", "java.sql.SQLException", "org.hibernate.engine.spi.SharedSessionContractImplementor")
                                        .build()
                                        .apply(getCursor(), md.getCoordinates().replace());
                                md = updatedParam.withId(md.getId()).withBody(md.getBody());
                            }
                            if (NULL_SAFE_SET.matches(md, cd)) {
                                md = changeParameterTypes(md, Collections.singletonList("value"));
                            }
                            if (DEEP_COPY.matches(md, cd) && parameterizedType != null) {
                                md = md.withReturnTypeExpression(parameterizedType.getTarget().withPrefix(Space.SINGLE_SPACE));
                                if (md.getReturnTypeExpression() != null) {
                                    md = md.withPrefix(md.getReturnTypeExpression().getPrefix());
                                }
                                md = changeParameterTypes(md, Collections.singletonList("value"));
                            }
                            if (DISASSEMBLE.matches(md, cd)) {
                                md = changeParameterTypes(md, Collections.singletonList("value"));
                                if (md.getBody() != null) {
                                    md = md.withBody(md.getBody().withStatements(ListUtils.map(md.getBody().getStatements(), stmt -> {
                                        if (stmt instanceof J.Return) {
                                            J.Return r = (J.Return) stmt;
                                            if (r.getExpression() instanceof J.TypeCast) {
                                                J.TypeCast tc = (J.TypeCast) r.getExpression();
                                                if (parameterizedType != null && TypeUtils.isOfType(parameterizedType.getTarget().getType(), tc.getClazz().getType())) {
                                                    return r.withExpression(tc.getExpression());
                                                }
                                            }
                                        }
                                        return stmt;
                                    })));
                                }
                            }
                            if (ASSEMBLE.matches(md, cd) && parameterizedType != null) {
                                md = md.withReturnTypeExpression(parameterizedType.getTarget().withPrefix(Space.SINGLE_SPACE));
                                if (md.getReturnTypeExpression() != null) {
                                    md = md.withPrefix(md.getReturnTypeExpression().getPrefix());
                                }
                                if (md.getBody() != null) {
                                    md = md.withBody(md.getBody().withStatements(ListUtils.map(md.getBody().getStatements(), stmt -> {
                                        if (stmt instanceof J.Return) {
                                            J.Return r = (J.Return) stmt;
                                            if (r.getExpression() != null && !TypeUtils.isOfType(parameterizedType.getTarget().getType(), r.getExpression().getType())) {
                                                return r.withExpression(new J.TypeCast(randomId(), Space.EMPTY, Markers.EMPTY, new J.ControlParentheses<>(randomId(), Space.EMPTY, Markers.EMPTY,
                                                        new JRightPadded<>(TypeTree.build("BigDecimal").withType(parameterizedType.getTarget().getType()), Space.EMPTY, Markers.EMPTY)), r.getExpression()));
                                            }
                                        }
                                        return stmt;
                                    })));
                                }
                            }
                            if (REPLACE.matches(md, cd) && parameterizedType != null) {
                                md = md.withReturnTypeExpression(parameterizedType.getTarget().withPrefix(Space.SINGLE_SPACE));
                                if (md.getReturnTypeExpression() != null) {
                                    md = md.withPrefix(md.getReturnTypeExpression().getPrefix());
                                }
                                md = changeParameterTypes(md, Arrays.asList("original", "target"));
                            }
                        }
                        updateCursor(md);
                        md = (J.MethodDeclaration) super.visitMethodDeclaration(md, ctx);
                        return maybeAutoFormat(method, md, ctx);
                    }

                    private J.MethodDeclaration changeParameterTypes(J.MethodDeclaration md, List<String> paramNames) {
                        J.FieldAccess parameterizedType = getCursor().getNearestMessage("parameterizedType");
                        if (md.getMethodType() != null) {
                            JavaType.Method met = md.getMethodType().withParameterTypes(ListUtils.map(md.getMethodType().getParameterTypes(),
                                    (index, type) -> {
                                        if (paramNames.contains(md.getMethodType().getParameterNames().get(index)) && parameterizedType != null) {
                                            type = TypeUtils.isOfType(JavaType.buildType("java.lang.Object"), type) ? parameterizedType.getTarget().getType() : type;
                                        }
                                        return type;
                                    }));
                            return md.withParameters(ListUtils.map(md.getParameters(), param -> {
                                if (param instanceof J.VariableDeclarations) {
                                    if (((J.VariableDeclarations) param).getVariables().stream().anyMatch(var -> paramNames.contains(var.getSimpleName()))) {
                                        if (parameterizedType != null) {
                                            param = ((J.VariableDeclarations) param).withType(parameterizedType.getTarget().getType()).withTypeExpression((TypeTree) parameterizedType.getTarget());
                                            param = ((J.VariableDeclarations) param).withVariables(ListUtils.map(((J.VariableDeclarations) param).getVariables(), var -> {
                                                if (paramNames.contains(var.getSimpleName())) {
                                                    var = var.withType(parameterizedType.getTarget().getType());
                                                    if (var.getVariableType() != null && parameterizedType != null && parameterizedType.getTarget().getType() != null) {
                                                        var = var.withVariableType(var.getVariableType().withType(parameterizedType.getTarget().getType()).withOwner(met));
                                                    }
                                                }
                                                return var;
                                            }));
                                        }
                                    }

                                }
                                return param;
                            }));
                        }
                        return md;
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        if (RESULT_SET_STRING_PARAM.matches(mi)) {
                            J.MethodDeclaration md = getCursor().firstEnclosing(J.MethodDeclaration.class);
                            J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
                            if (md != null && cd != null && NULL_SAFE_GET_INT.matches(md, cd)) {
                                mi = mi.withArguments(Collections.singletonList(((J.VariableDeclarations) md.getParameters().get(1)).getVariables().get(0).getName()));
                                if (mi.getMethodType() != null) {
                                    mi = mi.withMethodType(mi.getMethodType().withParameterTypes(Collections.singletonList(JavaType.buildType("int"))));
                                }
                            }
                        }
                        return mi;
                    }
                });
    }
}
