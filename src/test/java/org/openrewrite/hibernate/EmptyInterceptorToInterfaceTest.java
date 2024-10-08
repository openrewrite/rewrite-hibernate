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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;

class EmptyInterceptorToInterfaceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EmptyInterceptorToInterface())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-core")
          );
    }

    @DocumentExample
    @Test
    void shouldChangeEmptyInterceptor() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.EmptyInterceptor;
              
              class MyInterceptor extends EmptyInterceptor {
              
                  @Override
                  public String onPrepareStatement(String sql) {
                      return sql;
                  }
              }
              """,
            """
              import org.hibernate.Interceptor;
              import org.hibernate.resource.jdbc.spi.StatementInspector;
              
              class MyInterceptor implements Interceptor, StatementInspector {
              
                  @Override
                  public String inspect(String sql) {
                      return sql;
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldChangeWhenOverrideAnnotationMissing() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.EmptyInterceptor;
              
              class MyInterceptor extends EmptyInterceptor {
                  public String onPrepareStatement(String sql) {
                      return sql;
                  }
              }
              """,
            """
              import org.hibernate.Interceptor;
              import org.hibernate.resource.jdbc.spi.StatementInspector;
              
              class MyInterceptor implements Interceptor, StatementInspector {
                  public String inspect(String sql) {
                      return sql;
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldChangeEmptyInterceptorAlreadyImplements() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              public interface MyInterface { }
              """,
            SourceSpec::skip
          ),
          java(
            """
              import com.example.MyInterface;
              import org.hibernate.EmptyInterceptor;
              
              class MyInterceptor extends EmptyInterceptor implements MyInterface {
              
                  @Override
                  public String onPrepareStatement(String sql) {
                      return sql;
                  }
              }
              """,
            """
              import com.example.MyInterface;
              import org.hibernate.Interceptor;
              import org.hibernate.resource.jdbc.spi.StatementInspector;
              
              class MyInterceptor implements MyInterface, Interceptor, StatementInspector {
              
                  @Override
                  public String inspect(String sql) {
                      return sql;
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldRetainOtherAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.EmptyInterceptor;
              
              class MyInterceptor extends EmptyInterceptor {
              
                  @Override
                  @SuppressWarnings("ALL")
                  public String onPrepareStatement(String sql) {
                      return sql;
                  }
              }
              """,
            """
              import org.hibernate.Interceptor;
              import org.hibernate.resource.jdbc.spi.StatementInspector;
              
              class MyInterceptor implements Interceptor, StatementInspector {
              
                  @Override
                  @SuppressWarnings("ALL")
                  public String inspect(String sql) {
                      return sql;
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldChangeEmptyInterceptorNoPrepareStatement() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.EmptyInterceptor;
              
              class MyInterceptor extends EmptyInterceptor {
              
              }
              """,
            """
              import org.hibernate.Interceptor;
              
              class MyInterceptor implements Interceptor {
              
              }
              """
          )
        );
    }
}
