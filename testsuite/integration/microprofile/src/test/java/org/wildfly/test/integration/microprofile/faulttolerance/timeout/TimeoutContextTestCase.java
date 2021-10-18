/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.test.integration.microprofile.faulttolerance.timeout;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutionException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test case for https://issues.redhat.com/browse/WFLY-12982
 *
 * @author Radoslav Husar
 */
@RunWith(Arquillian.class)
public class TimeoutContextTestCase {

    @Deployment
    public static WebArchive createTestArchive() {
        return ShrinkWrap.create(WebArchive.class, TimeoutContextTestCase.class.getSimpleName() + ".war")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addPackage(TimeoutContextTestCase.class.getPackage())
//                .addAsManifestResource(createPermissionsXmlAsset(
//                        new FilePermission("<<ALL FILES>>", "read"),
//                        new PropertyPermission("*", "read,write"),
//                        new RuntimePermission("getenv.*"),
//                        new RuntimePermission("modifyThread")
//                ), "permissions.xml")
                ;
    }

    @Test
    public void testRequestContextActive(TimeoutBean timeoutBean) throws InterruptedException, ExecutionException {
        assertEquals("Hello bar", timeoutBean.greet());
    }

}
