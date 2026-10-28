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
package org.openrewrite.hibernate;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.trait.Namespaced;
import org.openrewrite.xml.tree.Xml;

import java.util.List;

import static java.util.Collections.singletonList;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeHibernateDialect extends Recipe {
    @Option(displayName = "Old dialect", description = "The fully qualified dialect class to replace.",
            example = "org.hibernate.dialect.MySQL8Dialect")
    String oldFullyQualifiedTypeName;

    @Option(displayName = "New dialect", description = "The fully qualified replacement dialect class.",
            example = "org.hibernate.dialect.MySQLDialect")
    String newFullyQualifiedTypeName;

    String displayName = "Change Hibernate dialect";

    String description = "Change a Hibernate dialect type and its configuration in properties, YAML, " +
            "`persistence.xml` and `hibernate.cfg.xml`. XML changes are limited to dialect properties " +
            "in standard persistence descriptors and Hibernate configuration files.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new ChangeType(oldFullyQualifiedTypeName, newFullyQualifiedTypeName, null));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles("**/{persistence.xml,hibernate.cfg.xml}"),
                new XmlIsoVisitor<ExecutionContext>() {
                    private final XPathMatcher persistenceProperty = new XPathMatcher(
                            "/*[local-name()='persistence']/*[local-name()='persistence-unit']/" +
                            "*[local-name()='properties']/*[local-name()='property'][@name='hibernate.dialect']");
                    private final XPathMatcher hibernateProperty = new XPathMatcher(
                            "/*[local-name()='hibernate-configuration']/*[local-name()='session-factory']/" +
                            "*[local-name()='property'][@name='hibernate.dialect' or @name='dialect']");

                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                        Xml.Tag t = super.visitTag(tag, ctx);
                        String filename = getCursor().firstEnclosingOrThrow(Xml.Document.class)
                                .getSourcePath().getFileName().toString();
                        if ("persistence.xml".equals(filename) && persistenceProperty.matches(getCursor()) &&
                            inDescriptorNamespace(true)) {
                            return t.withAttributes(ListUtils.map(t.getAttributes(), attribute ->
                                    "value".equals(attribute.getKeyAsString()) &&
                                    oldFullyQualifiedTypeName.equals(attribute.getValueAsString()) ?
                                            attribute.withValue(attribute.getValue().withValue(newFullyQualifiedTypeName)) : attribute));
                        }
                        if ("hibernate.cfg.xml".equals(filename) && hibernateProperty.matches(getCursor()) &&
                            inDescriptorNamespace(false) && t.getContent() != null && t.getContent().size() == 1 &&
                            oldFullyQualifiedTypeName.equals(t.getValue().orElse(null))) {
                            return t.withValue(newFullyQualifiedTypeName);
                        }
                        return t;
                    }

                    private boolean inDescriptorNamespace(boolean persistence) {
                        String namespace = namespace(getCursor());
                        if (!namespace.isEmpty() && !(persistence ?
                                "http://java.sun.com/xml/ns/persistence".equals(namespace) ||
                                "http://xmlns.jcp.org/xml/ns/persistence".equals(namespace) ||
                                "https://jakarta.ee/xml/ns/persistence".equals(namespace) :
                                "http://www.hibernate.org/xsd/orm/cfg".equals(namespace))) {
                            return false;
                        }
                        for (Cursor cursor = getCursor(); cursor.getValue() instanceof Xml.Tag; cursor = cursor.getParentTreeCursor()) {
                            if (!namespace.equals(namespace(cursor))) {
                                return false;
                            }
                        }
                        return true;
                    }

                    private String namespace(Cursor cursor) {
                        return new Namespaced(cursor).getNamespaceUri().orElse("");
                    }
                });
    }
}
