// SPDX-License-Identifier: Apache-2.0
// Copyright Red Hat Inc. and Hibernate Authors
package org.openrewrite.hibernate.jpa;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.tree.Xml;

/**
 * Bumps the {@code version} attribute on the {@code <persistence>} root element
 * from {@code "3.0"} / {@code "3.2"} to {@code "4.0"} and updates the {@code xmlns},
 * {@code xmlns:xsi}, and {@code xsi:schemaLocation} attributes to the JPA 4.0 URIs.
 */
public class UpdatePersistenceXmlVersion extends Recipe {

    private static final String PERSISTENCE_TAG = "persistence";

    @Override
    public String getDisplayName() {
        return "Update persistence.xml to JPA 4.0";
    }

    @Override
    public String getDescription() {
        return "Updates the version attribute and namespace declarations in persistence.xml from JPA 3.x to JPA 4.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        TreeVisitor<?, ExecutionContext> changeVersion = new ChangeTagAttribute(
                PERSISTENCE_TAG, "version", "4.0", null, null).getVisitor();

        TreeVisitor<?, ExecutionContext> changeXmlns = new ChangeTagAttribute(
                PERSISTENCE_TAG, "xmlns", "https://jakarta.ee/xml/ns/persistence", null, null).getVisitor();

        TreeVisitor<?, ExecutionContext> changeSchemaLocation = new ChangeTagAttribute(
                PERSISTENCE_TAG,
                "xsi:schemaLocation",
                "https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_4_0.xsd",
                null,
                null).getVisitor();

        return new XmlVisitor<ExecutionContext>() {
            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document d = (Xml.Document) changeVersion.visit(document, ctx);
                d = (Xml.Document) changeXmlns.visit(d, ctx);
                d = (Xml.Document) changeSchemaLocation.visit(d, ctx);
                return d;
            }
        };
    }
}
