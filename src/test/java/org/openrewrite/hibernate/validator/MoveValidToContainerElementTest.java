/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.hibernate.validator;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MoveValidToContainerElementTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MoveValidToContainerElement())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "jakarta.validation-api"));
    }

    @DocumentExample
    @Test
    void listField() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  @Valid
                  private List<Order> orders;
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  private List<@Valid Order> orders;
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void mapValuesRatherThanKeys() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.Map;

              class Customer {
                  @Valid
                  private Map<String, Order> orders;
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import java.util.Map;

              class Customer {
                  private Map<String, @Valid Order> orders;
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void optionalField() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.Optional;

              class Customer {
                  @Valid
                  private Optional<Order> order;
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import java.util.Optional;

              class Customer {
                  private Optional<@Valid Order> order;
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void setSubtypeOfIterable() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.LinkedHashSet;

              class Customer {
                  @Valid
                  private LinkedHashSet<Order> orders;
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import java.util.LinkedHashSet;

              class Customer {
                  private LinkedHashSet<@Valid Order> orders;
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void retainOtherAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import jakarta.validation.constraints.NotEmpty;
              import java.util.List;

              class Customer {
                  @Valid
                  @NotEmpty
                  private List<Order> orders;

                  @NotEmpty
                  @Valid
                  private List<Order> backOrders;
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import jakarta.validation.constraints.NotEmpty;
              import java.util.List;

              class Customer {
                  @NotEmpty
                  private List<@Valid Order> orders;

                  @NotEmpty
                  private List<@Valid Order> backOrders;
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void alongsideExistingTypeArgumentAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import jakarta.validation.constraints.NotNull;
              import java.util.List;

              class Customer {
                  @Valid
                  private List<@NotNull Order> orders;
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import jakarta.validation.constraints.NotNull;
              import java.util.List;

              class Customer {
                  private List<@Valid @NotNull Order> orders;
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void methodParameterAndReturnType() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  @Valid
                  public List<Order> getOrders() {
                      return null;
                  }

                  void addAll(@Valid List<Order> orders) {
                  }
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  public List<@Valid Order> getOrders() {
                      return null;
                  }

                  void addAll(List<@Valid Order> orders) {
                  }
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void nestedContainerCascadesToOuterElement() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  @Valid
                  private List<List<Order>> orders;
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  private List<@Valid List<Order>> orders;
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void typeUsePositionAfterModifiers() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import jakarta.validation.constraints.NotEmpty;
              import java.util.List;

              class Customer {
                  private @Valid List<Order> orders;

                  private @Valid @NotEmpty List<Order> backOrders;

                  public @Valid List<Order> getOrders() {
                      return orders;
                  }
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import jakarta.validation.constraints.NotEmpty;
              import java.util.List;

              class Customer {
                  private List<@Valid Order> orders;

                  private @NotEmpty List<@Valid Order> backOrders;

                  public List<@Valid Order> getOrders() {
                      return orders;
                  }
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void recordComponent() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.List;

              record Customer(@Valid List<Order> orders) {
              }

              class Order {
              }
              """,
            """
              import jakarta.validation.Valid;
              import java.util.List;

              record Customer(List<@Valid Order> orders) {
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void leaveNonContainersAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  @Valid
                  private Order order;

                  private @Valid Order shippedOrder;

                  @Valid
                  private Order[] orders;

                  @Valid
                  private List rawOrders;

                  @Valid
                  private Box<Order> boxedOrder;
              }

              class Order {
              }

              class Box<T> {
              }
              """
          )
        );
    }

    @Test
    void leaveWildcardsAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  @Valid
                  private List<? extends Order> orders;
              }

              class Order {
              }
              """
          )
        );
    }

    @Test
    void alreadyOnContainerElement() {
        rewriteRun(
          //language=java
          java(
            """
              import jakarta.validation.Valid;
              import java.util.List;

              class Customer {
                  private List<@Valid Order> orders;
              }

              class Order {
              }
              """
          )
        );
    }
}
