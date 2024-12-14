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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceLazyCollectionAnnotationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceLazyCollectionAnnotation())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-core-6+", "jakarta.persistence-api")
          );
    }

    @ParameterizedTest
    @CsvSource({
      //"LazyCollectionOption.FALSE, FetchType.EAGER, ElementCollection", // different import order
      "LazyCollectionOption.FALSE, FetchType.EAGER, ManyToMany",
      "LazyCollectionOption.FALSE, FetchType.EAGER, OneToMany",
      "LazyCollectionOption.TRUE,  FetchType.LAZY,  OneToOne",
      "LazyCollectionOption.TRUE,  FetchType.LAZY,  ManyToOne"
    })
    void methodAnnotation_shouldBeUpdated_whenFetchArgumentIsMissing(
      String oldArg, String newArg, String targetAnnotation) {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.%1$s;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items = new HashSet<>();
              
                  @LazyCollection(%2$s)
                  @%1$s
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """.formatted(targetAnnotation, oldArg),
            """
              import jakarta.persistence.FetchType;
              import jakarta.persistence.%1$s;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items = new HashSet<>();
              
                  @%1$s(fetch = %2$s)
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """.formatted(targetAnnotation, newArg)
          )
        );
    }

    @ParameterizedTest
    @CsvSource({
      //"LazyCollectionOption.FALSE, FetchType.EAGER, ElementCollection", // different import order
      "LazyCollectionOption.FALSE, FetchType.EAGER, ManyToMany",
      "LazyCollectionOption.FALSE, FetchType.EAGER, OneToMany",
      "LazyCollectionOption.TRUE,  FetchType.LAZY,  OneToOne",
      "LazyCollectionOption.TRUE,  FetchType.LAZY,  ManyToOne"
    })
    void fieldAnnotation_shouldBeUpdated_whenFetchArgumentIsMissing(
      String oldArg, String newArg, String targetAnnotation) {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.%1$s;
              
              import java.util.List;
              
              class SomeClass {
              
                  @LazyCollection(%2$s)
                  @%1$s
                  private List<Object> items;
              }
              """.formatted(targetAnnotation, oldArg),
            """
              import jakarta.persistence.FetchType;
              import jakarta.persistence.%1$s;
              
              import java.util.List;
              
              class SomeClass {
              
                  @%1$s(fetch = %2$s)
                  private List<Object> items;
              }
              """.formatted(targetAnnotation, newArg)
          )
        );
    }

    @Test
    void methodAnnotation_shouldNotBeUpdated_whenFetchArgumentIsPresent() {
        //language=java
        rewriteRun(
          // The before class makes no sense. This is intentional - the test case demonstrates
          // that the fetch argument is not changed, even if it doesn't match the old
          // LazyCollectionOption
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.FetchType;
              import jakarta.persistence.ManyToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items = new HashSet<>();
              
                  @LazyCollection(LazyCollectionOption.FALSE)
                  @ManyToMany(fetch = FetchType.LAZY)
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """,
            """
              import jakarta.persistence.FetchType;
              import jakarta.persistence.ManyToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items = new HashSet<>();
              
                  @ManyToMany(fetch = FetchType.LAZY)
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldAnnotation_shouldNotBeUpdated_whenFetchArgumentIsPresent() {
        //language=java
        rewriteRun(
          // The before class makes no sense. This is intentional - the test case demonstrates
          // that the fetch argument is not changed, even if it doesn't match the old
          // LazyCollectionOption
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.ElementCollection;
              import jakarta.persistence.FetchType;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  @LazyCollection(LazyCollectionOption.FALSE)
                  @ElementCollection(fetch = FetchType.LAZY)
                  private Set<Object> items;
              }
              """,
            """
              import jakarta.persistence.ElementCollection;
              import jakarta.persistence.FetchType;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  @ElementCollection(fetch = FetchType.LAZY)
                  private Set<Object> items;
              }
              """
          )
        );
    }

    @Test
    void methodAnnotation_shouldNotBeUpdated_whenLazyCollectionOptionIsExtra() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.ElementCollection;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items;
              
                  @LazyCollection(LazyCollectionOption.EXTRA)
                  @ElementCollection
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldAnnotation_shouldNotBeUpdated_whenLazyCollectionOptionIsExtra() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.ElementCollection;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  @LazyCollection(LazyCollectionOption.EXTRA)
                  @ElementCollection
                  private Set<Object> items;
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void methodAnnotation_shouldKeepPreviousArguments_whenFetchTypeIsAdded() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.CascadeType;
              import jakarta.persistence.OneToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items = new HashSet<>();
              
                  @LazyCollection(LazyCollectionOption.FALSE)
                  @OneToMany(cascade = { CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE })
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """,
            """
              import jakarta.persistence.CascadeType;
              import jakarta.persistence.FetchType;
              import jakarta.persistence.OneToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items = new HashSet<>();
              
                  @OneToMany(cascade = { CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE }, fetch = FetchType.EAGER)
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldAnnotation_shouldKeepPreviousArguments_whenFetchTypeIsAdded() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.CascadeType;
              import jakarta.persistence.OneToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  @LazyCollection(LazyCollectionOption.FALSE)
                  @OneToMany(cascade = { CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE })
                  private Set<Object> items = new HashSet<>();
              }
              """,
            """
              import jakarta.persistence.CascadeType;
              import jakarta.persistence.FetchType;
              import jakarta.persistence.OneToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  @OneToMany(cascade = { CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE }, fetch = FetchType.EAGER)
                  private Set<Object> items = new HashSet<>();
              }
              """
          )
        );
    }

    @Test
    void allLazyCollectionAnnotationsInClass_ShouldBeProcessed() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.ElementCollection;
              import jakarta.persistence.ManyToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items1;
                  private Set<Object> items2;
              
                  @ElementCollection
                  @LazyCollection(LazyCollectionOption.EXTRA)
                  private Set<Object> items3;
              
                  @LazyCollection
                  @ElementCollection
                  private Set<Object> items4;
              
                  @ManyToMany
                  @LazyCollection(LazyCollectionOption.TRUE)
                  public Set<Object> getItems1() {
                      return items1;
                  }
              
                  @LazyCollection(LazyCollectionOption.FALSE)
                  @ManyToMany
                  public Set<Object> getItems2() {
                      return items2;
                  }
              }
              """,
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import jakarta.persistence.ElementCollection;
              import jakarta.persistence.FetchType;
              import jakarta.persistence.ManyToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items1;
                  private Set<Object> items2;
              
                  @ElementCollection
                  @LazyCollection(LazyCollectionOption.EXTRA)
                  private Set<Object> items3;
              
                  @ElementCollection(fetch = FetchType.LAZY)
                  private Set<Object> items4;
              
                  @ManyToMany(fetch = FetchType.LAZY)
                  public Set<Object> getItems1() {
                      return items1;
                  }
              
                  @ManyToMany(fetch = FetchType.EAGER)
                  public Set<Object> getItems2() {
                      return items2;
                  }
              }
              """
          )
        );
    }

    @Test
    void regression_indentationIsWrong_whenSeveralAnnotationsArePresent() {
        //language=java
        rewriteRun(
          java(
            """
              import org.hibernate.annotations.LazyCollection;
              import org.hibernate.annotations.LazyCollectionOption;
              import org.hibernate.annotations.Cascade;
              import org.hibernate.annotations.CascadeType;
              import jakarta.persistence.JoinColumn;
              import jakarta.persistence.JoinTable;
              import jakarta.persistence.ManyToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items = new HashSet<>();
              
                  @Cascade(CascadeType.MERGE)
                  @LazyCollection(LazyCollectionOption.FALSE)
                  @ManyToMany
                  @JoinTable(name = "A_B", joinColumns = @JoinColumn(name = "A_ID"), inverseJoinColumns = @JoinColumn(name = "B_ID"))
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """,
            """
              import org.hibernate.annotations.Cascade;
              import org.hibernate.annotations.CascadeType;
              import jakarta.persistence.FetchType;
              import jakarta.persistence.JoinColumn;
              import jakarta.persistence.JoinTable;
              import jakarta.persistence.ManyToMany;
              
              import java.util.HashSet;
              import java.util.Set;
              
              class SomeClass {
              
                  private Set<Object> items = new HashSet<>();
              
                  @Cascade(CascadeType.MERGE)
                  @ManyToMany(fetch = FetchType.EAGER)
                  @JoinTable(name = "A_B", joinColumns = @JoinColumn(name = "A_ID"), inverseJoinColumns = @JoinColumn(name = "B_ID"))
                  public Set<Object> getItems() {
                      return items;
                  }
              }
              """
          )
        );
    }
}
