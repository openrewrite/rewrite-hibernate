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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindImplementations;
import org.openrewrite.java.search.FindMethodDeclaration;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
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
        return Preconditions.check(Preconditions.and(
                new FindImplementations(USER_TYPE).getVisitor(),
                // This method only exists on the Hibernate 6 variant of UserType, so as a precondition this shouldn't exist
                Preconditions.not(new FindMethodDeclaration("* getSqlType()", true).getVisitor())
        ), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = classDecl;
                J.FieldAccess parameterizedType = getReturnedClass(cd);
                cd = cd.withImplements(ListUtils.map(cd.getImplements(), impl -> {
                    if (TypeUtils.isAssignableTo(USER_TYPE, impl.getType()) && parameterizedType != null) {
                        return TypeTree.build("UserType<" + parameterizedType.getTarget() + ">").withType(JavaType.buildType(USER_TYPE)).withPrefix(Space.SINGLE_SPACE);
                    }
                    return impl;
                }));
                if (parameterizedType != null) {
                    getCursor().putMessage("parameterizedType", parameterizedType);
                }
                return super.visitClassDeclaration(cd, ctx);
            }

            @SuppressWarnings("ConstantConditions")
            private J.@Nullable FieldAccess getReturnedClass(J.ClassDeclaration cd) {
                AtomicReference<J.FieldAccess> reference = new AtomicReference<>();
                new JavaIsoVisitor<AtomicReference<J.FieldAccess>>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, AtomicReference<J.FieldAccess> ref) {
                        // Only visit top level method returnedClass
                        return RETURNED_CLASS.matches(method, cd) ? super.visitMethodDeclaration(method, ref) : method;
                    }

                    @Override
                    public J.Return visitReturn(J.Return _return, AtomicReference<J.FieldAccess> ref) {
                        Expression expression = _return.getExpression();
                        if (expression instanceof J.FieldAccess &&
                                "class".equals(((J.FieldAccess) expression).getSimpleName())) {
                            ref.set((J.FieldAccess) expression);
                        }
                        return _return;
                    }
                }.visitNonNull(cd, reference);
                return reference.get();
            }

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = method;
                J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
                J.FieldAccess parameterizedType = getCursor().getNearestMessage("parameterizedType");
                if (cd == null || parameterizedType == null) {
                    return md;
                }
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
                } else if (RETURNED_CLASS.matches(md, cd)) {
                    md = md.withReturnTypeExpression(TypeTree.build("Class<" + parameterizedType.getTarget() + ">"));
                    if (md.getReturnTypeExpression() != null) {
                        md = md.withPrefix(md.getReturnTypeExpression().getPrefix());
                    }
                } else if (EQUALS.matches(md, cd)) {
                    md = changeParameterTypes(md, Arrays.asList(0, 1), parameterizedType);
                } else if (HASHCODE.matches(md, cd)) {
                    md = changeParameterTypes(md, singletonList(0), parameterizedType);
                } else if (NULL_SAFE_GET_STRING_ARRAY.matches(md, cd)) {
                    String template = "@Override\n" +
                                      "public BigDecimal nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {\n" +
                                      "}";
                    J.MethodDeclaration updatedParam = JavaTemplate.builder(template)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hibernate-core"))
                            .imports("java.math.BigDecimal", "java.sql.ResultSet", "java.sql.SQLException", "org.hibernate.engine.spi.SharedSessionContractImplementor")
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace());
                    md = updatedParam.withId(md.getId()).withBody(md.getBody());
                } else if (NULL_SAFE_SET.matches(md, cd)) {
                    md = changeParameterTypes(md, singletonList(1), parameterizedType);
                } else if (DEEP_COPY.matches(md, cd)) {
                    md = md.withReturnTypeExpression(parameterizedType.getTarget().withPrefix(Space.SINGLE_SPACE));
                    if (md.getReturnTypeExpression() != null) {
                        md = md.withPrefix(md.getReturnTypeExpression().getPrefix());
                    }
                    md = changeParameterTypes(md, singletonList(0), parameterizedType);
                } else if (DISASSEMBLE.matches(md, cd)) {
                    md = changeParameterTypes(md, singletonList(0), parameterizedType);
                    if (md.getBody() != null) {
                        md = md.withBody(md.getBody().withStatements(ListUtils.map(md.getBody().getStatements(), stmt -> {
                            if (stmt instanceof J.Return) {
                                J.Return r = (J.Return) stmt;
                                if (r.getExpression() instanceof J.TypeCast) {
                                    J.TypeCast tc = (J.TypeCast) r.getExpression();
                                    if (TypeUtils.isOfType(parameterizedType.getTarget().getType(), tc.getClazz().getType())) {
                                        return r.withExpression(tc.getExpression());
                                    }
                                }
                            }
                            return stmt;
                        })));
                    }
                } else if (ASSEMBLE.matches(md, cd)) {
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
                } else if (REPLACE.matches(md, cd)) {
                    md = md.withReturnTypeExpression(parameterizedType.getTarget().withPrefix(Space.SINGLE_SPACE));
                    if (md.getReturnTypeExpression() != null) {
                        md = md.withPrefix(md.getReturnTypeExpression().getPrefix());
                    }
                    md = changeParameterTypes(md, Arrays.asList(0, 1), parameterizedType);
                }
                updateCursor(md);
                md = (J.MethodDeclaration) super.visitMethodDeclaration(md, ctx);
                return maybeAutoFormat(method, md, ctx);
            }

            private J.MethodDeclaration changeParameterTypes(J.MethodDeclaration md, List<Integer> paramIndexes, J.FieldAccess parameterizedType) {
                if (md.getMethodType() != null) {
                    JavaType.Method met = md.getMethodType().withParameterTypes(ListUtils.map(md.getMethodType().getParameterTypes(),
                            (index, type) -> {
                                if (paramIndexes.contains(index)) {
                                    type = TypeUtils.isOfType(JavaType.buildType("java.lang.Object"), type) ? parameterizedType.getTarget().getType() : type;
                                }
                                return type;
                            }));
                    return md.withParameters(ListUtils.map(md.getParameters(), (index, param) -> {
                        if (param instanceof J.VariableDeclarations && paramIndexes.contains(index)) {
                            param = ((J.VariableDeclarations) param)
                                    .withType(parameterizedType.getTarget().getType()).withTypeExpression((TypeTree) parameterizedType.getTarget())
                                    .withVariables(ListUtils.map(((J.VariableDeclarations) param).getVariables(), var -> {
                                        var = var.withType(parameterizedType.getTarget().getType());
                                        if (var.getVariableType() != null && parameterizedType.getTarget().getType() != null) {
                                            var = var.withVariableType(var.getVariableType().withType(parameterizedType.getTarget().getType()).withOwner(met));
                                        }
                                        return var;
                                    }));
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
                        mi = mi.withArguments(singletonList(((J.VariableDeclarations) md.getParameters().get(1)).getVariables().get(0).getName()));
                        if (mi.getMethodType() != null) {
                            mi = mi.withMethodType(mi.getMethodType().withParameterTypes(singletonList(JavaType.buildType("int"))));
                        }
                    }
                }
                return mi;
            }
        });
    }
}
