/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateToHibernate61Test implements RewriteTest {

    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.hibernate", "org.openrewrite.java.migrate.jakarta")
            .build()
            .activateRecipes("org.openrewrite.hibernate.MigrateToHibernate61"))
          .parser(JavaParser.fromJavaVersion().classpath("hibernate-core", "javax.persistence-api"));
    }

    @Test
    void groupIdHibernateOrmRenamed() {
        rewriteRun(
          mavenProject(
            "Sample",
            //language=xml
            pomXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.hibernate</groupId>
                      <artifactId>hibernate-core</artifactId>
                      <version>5.6.15.Final</version>
                    </dependency>
                  </dependencies>
                </project>
                """, spec -> spec.after(actual -> {
                  Matcher matcher = Pattern.compile("<version>(6\\.1\\.\\d+\\.Final)</version>").matcher(actual);
                  assertTrue(matcher.find());
                  return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>demo</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                        <dependency>
                          <groupId>org.hibernate.orm</groupId>
                          <artifactId>hibernate-core</artifactId>
                          <version>%s</version>
                        </dependency>
                      </dependencies>
                    </project>
                    """.formatted(matcher.group(1));
              })
            ),
            //language=java
            srcMainJava(
              java("""
                public class TestApplication {
                }
                """
              )
            )
          )
        );
    }

    @Test
    void groupIdHypersistenceUtilsRenamedAndPackageUpdated() {
        rewriteRun(
          mavenProject(
            "Sample",
            //language=xml
            pomXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.vladmihalcea</groupId>
                      <artifactId>hibernate-types-52</artifactId>
                      <version>2.17.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """, spec -> spec.after(actual -> {
                  Matcher matcher = Pattern.compile("<version>(3\\.5\\.\\d+)</version>").matcher(actual);
                  assertTrue(matcher.find());
                  return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>demo</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                        <dependency>
                          <groupId>io.hypersistence</groupId>
                          <artifactId>hypersistence-utils-hibernate-60</artifactId>
                          <version>%s</version>
                        </dependency>
                      </dependencies>
                    </project>
                      """.formatted(matcher.group(1));
              })
            ),
            //language=java
            srcMainJava(
              java("""
                import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
                                
                public class TestApplication {
                }
                """, """
                import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
                                
                public class TestApplication {
                }
                """
              )
            )
          )
        );
    }

    @Test
    void hypersistenceUtilsTypesMigrated() {
        rewriteRun(
          mavenProject(
            "Sample",
            //language=xml
            pomXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.hibernate</groupId>
                      <artifactId>hibernate-core</artifactId>
                      <version>5.6.15.Final</version>
                    </dependency>
                    <dependency>
                      <groupId>com.vladmihalcea</groupId>
                      <artifactId>hibernate-types-52</artifactId>
                      <version>2.17.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """, spec -> spec.after(actual -> {
                  Matcher hibernateMatcher = Pattern.compile("<version>(6\\.1\\.\\d+\\.Final)</version>").matcher(actual);
                  assertTrue(hibernateMatcher.find());
                  Matcher hypersistenceMatcher = Pattern.compile("<version>(3\\.5\\.\\d+)</version>").matcher(actual);
                  assertTrue(hypersistenceMatcher.find());
                  return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>demo</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                        <dependency>
                          <groupId>org.hibernate.orm</groupId>
                          <artifactId>hibernate-core</artifactId>
                          <version>%s</version>
                        </dependency>
                        <dependency>
                          <groupId>io.hypersistence</groupId>
                          <artifactId>hypersistence-utils-hibernate-60</artifactId>
                          <version>%s</version>
                        </dependency>
                      </dependencies>
                    </project>
                    """.formatted(hibernateMatcher.group(1), hypersistenceMatcher.group(1));
              })
            ),
            //language=java
            srcMainJava(
              java("""
                    import com.vladmihalcea.hibernate.type.json.JsonStringType;
                    import org.hibernate.annotations.Type;
                    import org.hibernate.annotations.TypeDef;
                    import org.hibernate.annotations.TypeDefs;

                    import javax.persistence.Basic;
                    import javax.persistence.Column;
                    import javax.persistence.Entity;
                    import javax.persistence.Id;
                    import javax.persistence.Table;
                    import java.io.Serializable;
                    import java.util.Map;

                    @Entity
                    @Table(name = "schema_revisions")
                    @TypeDefs({@TypeDef(name = "json", typeClass = JsonStringType.class)})
                    public class SchemaRevisionEntity implements Serializable {
                        @Id
                        @Basic(optional = false)
                        @Column(name = "name", nullable = false, updatable = false)
                        private String name;

                        @Type(type = "json")
                        @Column(name = "schema_metadata", nullable = false)
                        private SchemaRevisionMetadataEntity metadata;

                        public static class SchemaRevisionMetadataEntity implements Serializable {
                            private Map<String, String> userMetadata;
                        }
                    }
                  """,
                """
                    import io.hypersistence.utils.hibernate.type.json.JsonType;
                    import org.hibernate.annotations.Type;
                      
                    import jakarta.persistence.Basic;
                    import jakarta.persistence.Column;
                    import jakarta.persistence.Entity;
                    import jakarta.persistence.Id;
                    import jakarta.persistence.Table;
                    import java.io.Serializable;
                    import java.util.Map;
                      
                    @Entity
                    @Table(name = "schema_revisions")
                    public class SchemaRevisionEntity implements Serializable {
                        @Id
                        @Basic(optional = false)
                        @Column(name = "name", nullable = false, updatable = false)
                        private String name;
                      
                        @Type(value = JsonType.class)
                        @Column(name = "schema_metadata", nullable = false)
                        private SchemaRevisionMetadataEntity metadata;
                      
                        public static class SchemaRevisionMetadataEntity implements Serializable {
                            private Map<String, String> userMetadata;
                        }
                    }
                  """
              )
            )
          )
        );
    }

    @Test
    void typeDefsAnnotationIsNotRemovedWhenHypersistenceUtilsAreNotBeingUsed() {
        rewriteRun(
          mavenProject(
            "Sample",
            //language=java
            srcMainJava(
              java("class SomeCustomClass {}"),
              java("""
                    import org.hibernate.annotations.Type;
                    import org.hibernate.annotations.TypeDef;
                    import org.hibernate.annotations.TypeDefs;

                    import javax.persistence.Basic;
                    import javax.persistence.Column;
                    import javax.persistence.Entity;
                    import javax.persistence.Id;
                    import javax.persistence.Table;
                    import java.io.Serializable;
                    import java.util.Map;

                    @Entity
                    @Table(name = "schema_revisions")
                    @TypeDefs({@TypeDef(name = "json", typeClass = SomeCustomClass.class)})
                    public class SchemaRevisionEntity implements Serializable {
                        @Id
                        @Basic(optional = false)
                        @Column(name = "name", nullable = false, updatable = false)
                        private String name;

                        @Column(name = "schema_metadata", nullable = false)
                        private SchemaRevisionMetadataEntity metadata;

                        public static class SchemaRevisionMetadataEntity implements Serializable {
                            private Map<String, String> userMetadata;
                        }
                    }
                  """,
                """
                    import org.hibernate.annotations.Type;
                    import org.hibernate.annotations.TypeDef;
                    import org.hibernate.annotations.TypeDefs;

                    import jakarta.persistence.Basic;
                    import jakarta.persistence.Column;
                    import jakarta.persistence.Entity;
                    import jakarta.persistence.Id;
                    import jakarta.persistence.Table;
                    import java.io.Serializable;
                    import java.util.Map;

                    @Entity
                    @Table(name = "schema_revisions")
                    @TypeDefs({@TypeDef(name = "json", typeClass = SomeCustomClass.class)})
                    public class SchemaRevisionEntity implements Serializable {
                        @Id
                        @Basic(optional = false)
                        @Column(name = "name", nullable = false, updatable = false)
                        private String name;

                        @Column(name = "schema_metadata", nullable = false)
                        private SchemaRevisionMetadataEntity metadata;

                        public static class SchemaRevisionMetadataEntity implements Serializable {
                            private Map<String, String> userMetadata;
                        }
                    }
                  """
              )
            )
          )
        );
    }
}

