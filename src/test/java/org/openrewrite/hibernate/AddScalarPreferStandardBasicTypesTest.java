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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddScalarPreferStandardBasicTypesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddScalarPreferStandardBasicTypes())
          .parser(JavaParser.fromJavaVersion().classpath("hibernate-core", "javax.persistence-api"));
    }

    @DocumentExample
    @Test
    void addScalarPreferStandardBasicTypes_chainingMultipleTimes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.Session;
              import org.hibernate.type.StringType;
              import org.hibernate.type.IntegerType;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", new StringType())
                          .addScalar("age", IntegerType.INSTANCE)
                          .list();
                  }
              }
              """,
            """
              import org.hibernate.Session;
              import org.hibernate.type.StandardBasicTypes;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", StandardBasicTypes.STRING)
                          .addScalar("age", StandardBasicTypes.INTEGER)
                          .list();
                  }
              }
              """
          ));
    }

    @Test
    void addScalarPreferStandardBasicTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.Session;
              import org.hibernate.type.*;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", new StringType())
                          .addScalar("age", new IntegerType())
                          .addScalar("l", new LongType())
                          .addScalar("bool", new BooleanType())
                          .addScalar("numbool", new NumericBooleanType())
                          .addScalar("tf", new TrueFalseType())
                          .addScalar("yn", new YesNoType())
                          .addScalar("b", new ByteType())
                          .addScalar("sh", new ShortType())
                          .addScalar("fl", new FloatType())
                          .addScalar("dbl", new DoubleType())
                          .addScalar("bigint", new BigIntegerType())
                          .addScalar("bigdec", new BigDecimalType())
                          .addScalar("char", new CharacterType())
                          .addScalar("nstring", new StringNVarcharType())
                          .addScalar("url", new UrlType())
                          .addScalar("time", new TimeType())
                          .addScalar("date", new DateType())
                          .addScalar("timestamp", new TimestampType())
                          .addScalar("cal", new CalendarType())
                          .addScalar("caldate", new CalendarDateType())
                          .addScalar("class", new ClassType())
                          .addScalar("locale", new LocaleType())
                          .addScalar("curr", new CurrencyType())
                          .addScalar("tz", new TimeZoneType())
                          .addScalar("uuid", new UUIDBinaryType())
                          .addScalar("uuidc", new UUIDCharType())
                          .addScalar("bin", new BinaryType())
                          .addScalar("binw", new WrapperBinaryType())
                          .addScalar("row", new RowVersionType())
                          .addScalar("img", new ImageType())
                          .addScalar("blob", new BlobType())
                          .addScalar("matblob", new MaterializedBlobType())
                          .addScalar("char_array", new CharArrayType())
                          .addScalar("character_array", new CharacterArrayType())
                          .addScalar("text", new TextType())
                          .addScalar("ntext", new NTextType())
                          .addScalar("clob", new ClobType())
                          .addScalar("nclob", new NClobType())
                          .addScalar("mat_clob", new MaterializedClobType())
                          .addScalar("mat_nclob", new MaterializedNClobType())
                          // following have no StandardBasicTypes constants in Hibernate 5
                          .addScalar("dob", new LocalDateType()) // could be changed to LocalDateType.INSTANCE
                          .addScalar("creation_date", new InstantType())
                          .list();
                  }
              }
              """,
            """
              import org.hibernate.Session;
              import org.hibernate.type.BasicTypeReference;
              import org.hibernate.type.InstantType;
              import org.hibernate.type.LocalDateType;
              import org.hibernate.type.StandardBasicTypes;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", StandardBasicTypes.STRING)
                          .addScalar("age", StandardBasicTypes.INTEGER)
                          .addScalar("l", StandardBasicTypes.LONG)
                          .addScalar("bool", StandardBasicTypes.BOOLEAN)
                          .addScalar("numbool", StandardBasicTypes.NUMERIC_BOOLEAN)
                          .addScalar("tf", StandardBasicTypes.TRUE_FALSE)
                          .addScalar("yn", StandardBasicTypes.YES_NO)
                          .addScalar("b", StandardBasicTypes.BYTE)
                          .addScalar("sh", StandardBasicTypes.SHORT)
                          .addScalar("fl", StandardBasicTypes.FLOAT)
                          .addScalar("dbl", StandardBasicTypes.DOUBLE)
                          .addScalar("bigint", StandardBasicTypes.BIG_INTEGER)
                          .addScalar("bigdec", StandardBasicTypes.BIG_DECIMAL)
                          .addScalar("char", StandardBasicTypes.CHARACTER)
                          .addScalar("nstring", StandardBasicTypes.NSTRING)
                          .addScalar("url", StandardBasicTypes.URL)
                          .addScalar("time", StandardBasicTypes.TIME)
                          .addScalar("date", StandardBasicTypes.DATE)
                          .addScalar("timestamp", StandardBasicTypes.TIMESTAMP)
                          .addScalar("cal", StandardBasicTypes.CALENDAR)
                          .addScalar("caldate", StandardBasicTypes.CALENDAR_DATE)
                          .addScalar("class", StandardBasicTypes.CLASS)
                          .addScalar("locale", StandardBasicTypes.LOCALE)
                          .addScalar("curr", StandardBasicTypes.CURRENCY)
                          .addScalar("tz", StandardBasicTypes.TIMEZONE)
                          .addScalar("uuid", StandardBasicTypes.UUID_BINARY)
                          .addScalar("uuidc", StandardBasicTypes.UUID_CHAR)
                          .addScalar("bin", StandardBasicTypes.BINARY)
                          .addScalar("binw", StandardBasicTypes.WRAPPER_BINARY)
                          .addScalar("row", StandardBasicTypes.ROW_VERSION)
                          .addScalar("img", StandardBasicTypes.IMAGE)
                          .addScalar("blob", StandardBasicTypes.BLOB)
                          .addScalar("matblob", StandardBasicTypes.MATERIALIZED_BLOB)
                          .addScalar("char_array", StandardBasicTypes.CHAR_ARRAY)
                          .addScalar("character_array", StandardBasicTypes.CHARACTER_ARRAY)
                          .addScalar("text", StandardBasicTypes.TEXT)
                          .addScalar("ntext", StandardBasicTypes.NTEXT)
                          .addScalar("clob", StandardBasicTypes.CLOB)
                          .addScalar("nclob", StandardBasicTypes.NCLOB)
                          .addScalar("mat_clob", StandardBasicTypes.MATERIALIZED_CLOB)
                          .addScalar("mat_nclob", StandardBasicTypes.MATERIALIZED_NCLOB)
                          // following have no StandardBasicTypes constants in Hibernate 5
                          .addScalar("dob", new LocalDateType()) // could be changed to LocalDateType.INSTANCE
                          .addScalar("creation_date", new InstantType())
                          .list();
                  }
              }
              """
          ));
    }

    @Test
    void addScalarPreferStandardBasicTypes_justStringType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.Session;
              import org.hibernate.type.StringType;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", new StringType())
                          .list();
                  }
              }
              """,
            """
              import org.hibernate.Session;
              import org.hibernate.type.StandardBasicTypes;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", StandardBasicTypes.STRING)
                          .list();
                  }
              }
              """
          ));
    }

    @Test
    void addScalarPreferStandardBasicTypes_justIntegerType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.Session;
              import org.hibernate.type.IntegerType;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("age", IntegerType.INSTANCE)
                          .list();
                  }
              }
              """,
            """
              import org.hibernate.Session;
              import org.hibernate.type.StandardBasicTypes;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("age", StandardBasicTypes.INTEGER)
                          .list();
                  }
              }
              """
          ));
    }

    @Test
    void addScalarPreferStandardBasicTypes_justLongType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.Session;
              import org.hibernate.type.LongType;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("long_number", LongType.INSTANCE)
                          .list();
                  }
              }
              """,
            """
              import org.hibernate.Session;
              import org.hibernate.type.StandardBasicTypes;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("long_number", StandardBasicTypes.LONG)
                          .list();
                  }
              }
              """
          ));
    }

    @Test
    void addScalarPreferStandardBasicTypes_justStringType_usingInstance() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.Session;
              import org.hibernate.type.StringType;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", StringType.INSTANCE)
                          .list();
                  }
              }
              """,
            """
              import org.hibernate.Session;
              import org.hibernate.type.StandardBasicTypes;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", StandardBasicTypes.STRING)
                          .list();
                  }
              }
              """
          ));
    }

    @Test
    void addScalarInvocationWithNoChange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.Session;
              import org.hibernate.type.StandardBasicTypes;
              import org.hibernate.type.LocalDateType;

              class MyRepository {
                  void callAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .addScalar("name", StandardBasicTypes.STRING)
                          .addScalar("age", StandardBasicTypes.INTEGER)
                          .addScalar("dob", new LocalDateType())
                          .list();
                  }
              }
              """
          ));
    }

    @Test
    void noAddScalarInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.Session;

              class MyRepository {
                  void noCallToAddScalar(Session session) {
                      session.createNativeQuery("select * from foo")
                          .list();
                  }
              }
              """
          ));
    }

}
