/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * $Id$
 */
package org.forgerock.openidm.provisioner.openicf.commons;

import org.forgerock.openidm.provisioner.openicf.connector.TestConnector;
import org.codehaus.jackson.map.ObjectMapper;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URL;
import java.util.Map;


public class ObjectClassInfoHelperTest {
    @Test
    public void testGetNativeJavaType() throws Exception {
        TestConnector connector = new TestConnector();
        Schema schema = connector.schema();
        ObjectClassInfo account = schema.findObjectClassInfo("__ACCOUNT__");
        Map schemaMAP = ConnectorUtil.getObjectClassInfoMap(account);
        Assert.assertNotNull(schema);
        try {
            ObjectMapper mapper = new ObjectMapper();
            URL root = ObjectClassInfoHelperTest.class.getResource("/");
            mapper.writeValue(new File((new URL(root, "schema.json")).toURI()), schemaMAP);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }



}