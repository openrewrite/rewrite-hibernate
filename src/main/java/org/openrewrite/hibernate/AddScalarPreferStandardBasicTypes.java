/*
 * Copyright 2025 the original author or authors.
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

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

public class AddScalarPreferStandardBasicTypes extends Recipe {

    static final MethodMatcher ADD_SCALAR_MATCHER =
            new MethodMatcher("org.hibernate.query.NativeQuery addScalar(String, org.hibernate.type.Type)");

    private static final String STANDARD_BASIC_TYPES_FQN = "org.hibernate.type.StandardBasicTypes";

    private static final Map<String, String> CONVERTIBLE_TYPES = new HashMap<>();

    static {
        // value is constant name in StandardBasicTypes
        CONVERTIBLE_TYPES.put("org.hibernate.type.BooleanType", "BOOLEAN");
        CONVERTIBLE_TYPES.put("org.hibernate.type.NumericBooleanType", "NUMERIC_BOOLEAN");
        CONVERTIBLE_TYPES.put("org.hibernate.type.TrueFalseType", "TRUE_FALSE");
        CONVERTIBLE_TYPES.put("org.hibernate.type.YesNoType", "YES_NO");
        CONVERTIBLE_TYPES.put("org.hibernate.type.ByteType", "BYTE");
        CONVERTIBLE_TYPES.put("org.hibernate.type.ShortType", "SHORT");
        CONVERTIBLE_TYPES.put("org.hibernate.type.IntegerType", "INTEGER");
        CONVERTIBLE_TYPES.put("org.hibernate.type.LongType", "LONG");
        CONVERTIBLE_TYPES.put("org.hibernate.type.FloatType", "FLOAT");
        CONVERTIBLE_TYPES.put("org.hibernate.type.DoubleType", "DOUBLE");
        CONVERTIBLE_TYPES.put("org.hibernate.type.BigIntegerType", "BIG_INTEGER");
        CONVERTIBLE_TYPES.put("org.hibernate.type.BigDecimalType", "BIG_DECIMAL");
        CONVERTIBLE_TYPES.put("org.hibernate.type.CharacterType", "CHARACTER");
        CONVERTIBLE_TYPES.put("org.hibernate.type.StringType", "STRING");
        CONVERTIBLE_TYPES.put("org.hibernate.type.StringNVarcharType", "NSTRING");
        CONVERTIBLE_TYPES.put("org.hibernate.type.UrlType", "URL");
        CONVERTIBLE_TYPES.put("org.hibernate.type.TimeType", "TIME");
        CONVERTIBLE_TYPES.put("org.hibernate.type.DateType", "DATE");
        CONVERTIBLE_TYPES.put("org.hibernate.type.TimestampType", "TIMESTAMP");
        CONVERTIBLE_TYPES.put("org.hibernate.type.CalendarType", "CALENDAR");
        CONVERTIBLE_TYPES.put("org.hibernate.type.CalendarDateType", "CALENDAR_DATE");
        CONVERTIBLE_TYPES.put("org.hibernate.type.ClassType", "CLASS");
        CONVERTIBLE_TYPES.put("org.hibernate.type.LocaleType", "LOCALE");
        CONVERTIBLE_TYPES.put("org.hibernate.type.CurrencyType", "CURRENCY");
        CONVERTIBLE_TYPES.put("org.hibernate.type.TimeZoneType", "TIMEZONE");
        CONVERTIBLE_TYPES.put("org.hibernate.type.UUIDBinaryType", "UUID_BINARY");
        CONVERTIBLE_TYPES.put("org.hibernate.type.UUIDCharType", "UUID_CHAR");
        CONVERTIBLE_TYPES.put("org.hibernate.type.BinaryType", "BINARY");
        CONVERTIBLE_TYPES.put("org.hibernate.type.WrapperBinaryType", "WRAPPER_BINARY");
        CONVERTIBLE_TYPES.put("org.hibernate.type.RowVersionType", "ROW_VERSION");
        CONVERTIBLE_TYPES.put("org.hibernate.type.ImageType", "IMAGE");
        CONVERTIBLE_TYPES.put("org.hibernate.type.BlobType", "BLOB");
        CONVERTIBLE_TYPES.put("org.hibernate.type.MaterializedBlobType", "MATERIALIZED_BLOB");
        CONVERTIBLE_TYPES.put("org.hibernate.type.CharArrayType", "CHAR_ARRAY");
        CONVERTIBLE_TYPES.put("org.hibernate.type.CharacterArrayType", "CHARACTER_ARRAY");
        CONVERTIBLE_TYPES.put("org.hibernate.type.TextType", "TEXT");
        CONVERTIBLE_TYPES.put("org.hibernate.type.NTextType", "NTEXT");
        CONVERTIBLE_TYPES.put("org.hibernate.type.ClobType", "CLOB");
        CONVERTIBLE_TYPES.put("org.hibernate.type.NClobType", "NCLOB");
        CONVERTIBLE_TYPES.put("org.hibernate.type.MaterializedClobType", "MATERIALIZED_CLOB");
        CONVERTIBLE_TYPES.put("org.hibernate.type.MaterializedNClobType", "MATERIALIZED_NCLOB");
        CONVERTIBLE_TYPES.put("org.hibernate.type.SerializableType", "SERIALIZABLE");
    }

    @Override
    public String getDisplayName() {
        // language=markdown
        return "AddScalarPreferStandardBasicTypesForHibernate5";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Prefer the use of `StandardBasicTypes.*` in `NativeQuery.addScalar(...)` invocations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ADD_SCALAR_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);

                if (!ADD_SCALAR_MATCHER.matches(method)) {
                    return method;
                }

                Expression firstArg = method.getArguments().get(0);
                Expression secondArg = method.getArguments().get(1);
                JavaType secondArgType = secondArg.getType();
                if (secondArgType == null || (secondArg instanceof J.FieldAccess &&
                        TypeUtils.isOfClassType(((J.FieldAccess) secondArg).getTarget().getType(), STANDARD_BASIC_TYPES_FQN))) {
                    // Begins with StandardBasicTypes.*"
                    return method;
                }

                Optional<String> standardBasicTypesConstant = findConvertibleStandardBasicTypesConstant(secondArgType);
                if (standardBasicTypesConstant.isPresent()) {
                    maybeAddImport(STANDARD_BASIC_TYPES_FQN);
                    maybeRemoveImport(secondArgType.toString());
                    J.FieldAccess replacementArg = JavaTemplate.builder("StandardBasicTypes.#{}")
                            .imports(STANDARD_BASIC_TYPES_FQN)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hibernate-core-6.+"))
                            .build()
                            .apply(new Cursor(getCursor(), secondArg), secondArg.getCoordinates().replace(), standardBasicTypesConstant.get());
                    return method.withArguments(Arrays.asList(firstArg, replacementArg.withPrefix(secondArg.getPrefix())));
                }
                return method;
            }

            private Optional<String> findConvertibleStandardBasicTypesConstant(JavaType type) {
                if (type instanceof JavaType.FullyQualified) {
                    String searchedFQN = ((JavaType.FullyQualified) type).getFullyQualifiedName();
                    for (Map.Entry<String, String> entry : CONVERTIBLE_TYPES.entrySet()) {
                        if (TypeUtils.fullyQualifiedNamesAreEqual(searchedFQN, entry.getKey())) {
                            return Optional.of(CONVERTIBLE_TYPES.get(entry.getKey()));
                        }
                    }
                }
                return Optional.empty();
            }
        });
    }

}
