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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.RecipesThatMadeChanges;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateUserTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateUserType())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-core")
          );
    }

    @Test
    @DocumentExample
    void shouldMigrateUserType() {
        //language=java
        rewriteRun(
          java(
            """
            import org.hibernate.HibernateException;
            import org.hibernate.engine.spi.SharedSessionContractImplementor;
            import org.hibernate.usertype.UserType;

            import java.io.Serializable;
            import java.math.BigDecimal;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import java.sql.Types;
            import java.util.Objects;

            public class BigDecimalAsString implements UserType {

                @Override
                public int[] sqlTypes() {
                    return new int[]{Types.VARCHAR};
                }

                @Override
                public Class returnedClass() {
                    return BigDecimal.class;
                }

                @Override
                public boolean equals(Object x, Object y) {
                    return Objects.equals(x, y);
                }

                @Override
                public int hashCode(Object x) {
                    return Objects.hashCode(x);
                }

                @Override
                public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws SQLException {
                    String string = rs.getString(names[0]);
                    return string == null || rs.wasNull() ? null : new BigDecimal(string);
                }

                @Override
                public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws SQLException {
                    if (value == null) {
                        st.setNull(index, Types.VARCHAR);
                    } else {
                        st.setString(index, value.toString());
                    }
                }

                @Override
                public Object deepCopy(Object value) {
                    return value;
                }

                @Override
                public boolean isMutable() {
                    return false;
                }

                @Override
                public Serializable disassemble(Object value) {
                    return (BigDecimal) value;
                }

                @Override
                public Object assemble(Serializable cached, Object owner) {
                    return cached;
                }

                @Override
                public Object replace(Object original, Object target, Object owner) {
                    return original;
                }
            }
            """,
            """
            import org.hibernate.HibernateException;
            import org.hibernate.engine.spi.SharedSessionContractImplementor;
            import org.hibernate.usertype.UserType;

            import java.io.Serializable;
            import java.math.BigDecimal;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import java.sql.Types;
            import java.util.Objects;

            public class BigDecimalAsString implements UserType<BigDecimal> {

                @Override
                public int getSqlType() {
                    return Types.VARCHAR;
                }

                @Override
                public Class<BigDecimal> returnedClass() {
                    return BigDecimal.class;
                }

                @Override
                public boolean equals(BigDecimal x, BigDecimal y) {
                    return Objects.equals(x, y);
                }

                @Override
                public int hashCode(BigDecimal x) {
                    return Objects.hashCode(x);
                }

                @Override
                public BigDecimal nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
                    String string = rs.getString(position);
                    return string == null || rs.wasNull() ? null : new BigDecimal(string);
                }

                @Override
                public void nullSafeSet(PreparedStatement st, BigDecimal value, int index, SharedSessionContractImplementor session) throws SQLException {
                    if (value == null) {
                        st.setNull(index, Types.VARCHAR);
                    } else {
                        st.setString(index, value.toString());
                    }
                }

                @Override
                public BigDecimal deepCopy(BigDecimal value) {
                    return value;
                }

                @Override
                public boolean isMutable() {
                    return false;
                }

                @Override
                public Serializable disassemble(BigDecimal value) {
                    return value;
                }

                @Override
                public BigDecimal assemble(Serializable cached, Object owner) {
                    return (BigDecimal) cached;
                }

                @Override
                public BigDecimal replace(BigDecimal original, BigDecimal target, Object owner) {
                    return original;
                }
            }
            """
          )
        );
    }
}
