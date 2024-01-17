/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.subsystem;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Enumeration of the supported subsystem xml schemas.
 * @author Paul Ferraro
 */
public enum JGroupsSubsystemSchema implements SubsystemSchema<JGroupsSubsystemSchema> {

    VERSION_1_0(1, 0), // AS 7.0
    VERSION_1_1(1, 1), // AS 7.1
    VERSION_2_0(2, 0), // WildFly 8
    VERSION_3_0(3, 0), // WildFly 9
    VERSION_4_0(4, 0), // WildFly 10, EAP 7.0
    VERSION_5_0(5, 0), // WildFly 11, EAP 7.1
    VERSION_6_0(6, 0), // WildFly 12-16, EAP 7.2
    VERSION_7_0(7, 0), // WildFly 17-19, EAP 7.3
    VERSION_8_0(8, 0), // WildFly 20-26, EAP 7.4
    VERSION_9_0(9, 0), // WildFly 27-32, EAP 8.0
    VERSION_10_0(10, 0), // WildFly 33-present
    ;
    static final JGroupsSubsystemSchema CURRENT = VERSION_10_0;

    private final VersionedNamespace<IntVersion, JGroupsSubsystemSchema> namespace;

    JGroupsSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(JGroupsExtension.SUBSYSTEM_NAME, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, JGroupsSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        new JGroupsSubsystemXMLReader(this).readElement(reader, operations);
    }
}
