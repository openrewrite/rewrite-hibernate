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

import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.trait.Namespaced;
import org.openrewrite.xml.tree.Xml;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MigratePersistenceXmlToJakartaPersistence32 extends Recipe {
    private static final String NAMESPACE = "https://jakarta.ee/xml/ns/persistence";
    private static final String SCHEMA = NAMESPACE + "/persistence_3_2.xsd";
    private static final Pattern TOKEN = Pattern.compile("\\S+");

    @Getter
    final String displayName = "Migrate `persistence.xml` to Jakarta Persistence 3.2";

    @Getter
    final String description = "Update Jakarta Persistence 3.0 and 3.1 descriptors named `persistence.xml` to 3.2. " +
            "Preserve newer descriptors, namespace prefixes and unrelated schema locations.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles("**/persistence.xml"), new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Tag root = document.getRoot();
                Namespaced namespaced = new Namespaced(new Cursor(getCursor(), root));
                Map<String, String> namespaces = namespaced.getNamespaces();
                if (!"persistence".equals(namespaced.getLocalName().orElse(null)) ||
                    !NAMESPACE.equals(namespaces.get(namespaced.getNamespacePrefix().orElse("")))) {
                    return document;
                }
                String version = root.getAttributes().stream()
                        .filter(a -> "version".equals(a.getKeyAsString()))
                        .map(Xml.Attribute::getValueAsString).findFirst().orElse("");
                if (!"3.0".equals(version) && !"3.1".equals(version)) {
                    return document;
                }
                return document.withRoot(root.withAttributes(ListUtils.map(root.getAttributes(), attribute -> {
                    String name = attribute.getKeyAsString();
                    if ("version".equals(name)) {
                        return attribute.withValue(attribute.getValue().withValue("3.2"));
                    }
                    if (name.contains(":") && "schemaLocation".equals(Namespaced.extractLocalName(name)) &&
                        Namespaced.XML_SCHEMA_INSTANCE_URI.equals(namespaces.get(Namespaced.extractNamespacePrefix(name)))) {
                        return attribute.withValue(attribute.getValue().withValue(updateSchemaLocation(attribute.getValueAsString())));
                    }
                    return attribute;
                })));
            }
        });
    }

    private static String updateSchemaLocation(String value) {
        Matcher tokens = TOKEN.matcher(value);
        StringBuilder updated = new StringBuilder();
        int end = 0;
        while (tokens.find()) {
            String namespace = tokens.group();
            if (!tokens.find()) {
                return value;
            }
            if (NAMESPACE.equals(namespace)) {
                updated.append(value, end, tokens.start()).append(SCHEMA);
                end = tokens.end();
            }
        }
        return end == 0 ? value : updated.append(value, end, value.length()).toString();
    }
}
