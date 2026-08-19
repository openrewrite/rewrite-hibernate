// SPDX-License-Identifier: Apache-2.0
// Copyright Red Hat Inc. and Hibernate Authors
package org.openrewrite.hibernate.jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class UpdatePersistenceXmlVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdatePersistenceXmlVersion());
    }

    @DocumentExample
    @Test
    void updatesPersistenceXmlFromJpa30() {
        rewriteRun(
                xml(
                        //language=xml
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="3.0"
                                     xmlns="https://jakarta.ee/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_0.xsd">
                            <persistence-unit name="my-unit" transaction-type="JTA">
                            </persistence-unit>
                        </persistence>
                        """,
                        //language=xml
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="4.0"
                                     xmlns="https://jakarta.ee/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_4_0.xsd">
                            <persistence-unit name="my-unit" transaction-type="JTA">
                            </persistence-unit>
                        </persistence>
                        """
                )
        );
    }

    @Test
    void updatesPersistenceXmlFromJpa32() {
        rewriteRun(
                xml(
                        //language=xml
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="3.2"
                                     xmlns="https://jakarta.ee/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_2.xsd">
                            <persistence-unit name="my-unit" transaction-type="RESOURCE_LOCAL">
                            </persistence-unit>
                        </persistence>
                        """,
                        //language=xml
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="4.0"
                                     xmlns="https://jakarta.ee/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_4_0.xsd">
                            <persistence-unit name="my-unit" transaction-type="RESOURCE_LOCAL">
                            </persistence-unit>
                        </persistence>
                        """
                )
        );
    }

    @Test
    void noOpWhenAlreadyJpa40() {
        rewriteRun(
                xml(
                        //language=xml
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="4.0"
                                     xmlns="https://jakarta.ee/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_4_0.xsd">
                            <persistence-unit name="my-unit" transaction-type="JTA">
                            </persistence-unit>
                        </persistence>
                        """
                )
        );
    }

    @Test
    void updateVersionOnlyAttribute() {
        rewriteRun(
                xml(
                        //language=xml
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="3.0">
                            <persistence-unit name="my-unit">
                            </persistence-unit>
                        </persistence>
                        """,
                        //language=xml
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence version="4.0">
                            <persistence-unit name="my-unit">
                            </persistence-unit>
                        </persistence>
                        """
                )
        );
    }
}
