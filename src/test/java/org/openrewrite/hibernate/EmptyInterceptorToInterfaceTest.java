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
            }"""
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
            }"""
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
            
            }"""
          )
        );
    }

}