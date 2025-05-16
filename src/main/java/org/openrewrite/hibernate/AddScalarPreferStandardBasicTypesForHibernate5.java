package org.openrewrite.hibernate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AddScalarPreferStandardBasicTypesForHibernate5 extends Recipe {

    static final MethodMatcher ADD_SCALAR_MATCHER =
            new MethodMatcher("org.hibernate.query.NativeQuery addScalar(String, org.hibernate.type.Type)");

    private static final String STANDARD_BASIC_TYPES_FQN = "org.hibernate.type.StandardBasicTypes";

    private static final Map<String, String> CONVERTIBLE_TYPES;

    static {
        CONVERTIBLE_TYPES = new HashMap<>();
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
        return Preconditions.check(new UsesMethod<>(ADD_SCALAR_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        method = super.visitMethodInvocation(method, ctx);

                        if (!ADD_SCALAR_MATCHER.matches(method)) {
                            return method;
                        }

                        Expression firstArg = method.getArguments().get(0);
                        Expression secondArg = method.getArguments().get(1);

                        if (secondArg.getType() == null) {
                            return method;
                        }

                        assert secondArg.getType() != null;
                        if (secondArg instanceof J.FieldAccess) {
                            J.FieldAccess arg = (J.FieldAccess) secondArg;
                            if (arg.getTarget() instanceof J.Identifier
                                    && TypeUtils.isOfClassType(arg.getTarget().getType(), STANDARD_BASIC_TYPES_FQN)) {
                                // Begins with StandardBasicTypes.*"
                                return method;
                            }
                        }

                        Optional<String> standardBasicTypesConstant = findConvertibleStandardBasicTypesConstant(secondArg.getType());
                        if (standardBasicTypesConstant.isPresent()) {
                            maybeAddImport(STANDARD_BASIC_TYPES_FQN);
                            maybeRemoveImport(Objects.requireNonNull(secondArg.getType()).toString());
                            return method.withArguments(Arrays.asList(firstArg, standardBasicTypesDotConstantName(standardBasicTypesConstant.get()).withPrefix(secondArg.getPrefix())));
                        }
                        return method;
                    }

                    private J.FieldAccess standardBasicTypesDotConstantName(final String constantName) {
                        return new J.FieldAccess(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                new J.Identifier(
                                        Tree.randomId(),
                                        Space.EMPTY,
                                        Markers.EMPTY,
                                        Collections.emptyList(),
                                        "StandardBasicTypes",
                                        JavaType.ShallowClass.build(STANDARD_BASIC_TYPES_FQN),
                                        null
                                ),
                                new JLeftPadded<>(
                                        Space.EMPTY,
                                        new J.Identifier(
                                                Tree.randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                Collections.emptyList(),
                                                constantName,
                                                JavaType.ShallowClass.build(STANDARD_BASIC_TYPES_FQN + "." + constantName),
                                                null),
                                        Markers.EMPTY
                                ),
                                null
                        );
                    }

                    private Optional<String> findConvertibleStandardBasicTypesConstant(JavaType type) {
                        if (type instanceof JavaType.FullyQualified) {
                            String searchedFQN = ((JavaType.FullyQualified) type).getFullyQualifiedName();
                            for (Map.Entry<String, String> entry : CONVERTIBLE_TYPES.entrySet()) {
                                if (TypeUtils.fullyQualifiedNamesAreEqual(searchedFQN, entry.getKey())) {
                                    return Optional.of(CONVERTIBLE_TYPES.get(entry.getKey()));
                                }
                            }
                            return Optional.empty();
                        }
                        return Optional.empty();
                    }

                });
    }

}
