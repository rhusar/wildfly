/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.undertow.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.wildfly.clustering.marshalling.MarshallingTesterFactory;
import org.wildfly.clustering.marshalling.Tester;
import org.wildfly.clustering.marshalling.TesterFactory;
import org.wildfly.clustering.marshalling.junit.TesterFactorySource;
import org.wildfly.clustering.marshalling.protostream.util.StringKeyMapEntry;

/**
 * Validates that the entry published to the web routing registry, i.e. the route of a member together with the
 * addresses, per scheme, via which its peers can reach it, is marshallable.
 *
 * @author Radoslav Husar
 */
public class RoutingRegistryEntryMarshallingTestCase {

    @ParameterizedTest
    @TesterFactorySource(MarshallingTesterFactory.class)
    public void test(TesterFactory factory) {
        Tester<StringKeyMapEntry<Map<String, List<InetSocketAddress>>>> tester = factory.createTester((expected, actual) -> {
            assertEquals(expected.getKey(), actual.getKey());
            assertEquals(expected.getValue(), actual.getValue());
        });

        // A member whose server publishes no address reachable by its peers
        tester.accept(new StringKeyMapEntry<>("node1", Map.of()));

        tester.accept(new StringKeyMapEntry<>("node2", Map.of("http", List.of(InetSocketAddress.createUnresolved("node2.example.com", 8080)))));

        tester.accept(new StringKeyMapEntry<>("node3", Map.of(
                "ajp", List.of(InetSocketAddress.createUnresolved("192.0.2.3", 8009)),
                "http", List.of(InetSocketAddress.createUnresolved("192.0.2.3", 8080), InetSocketAddress.createUnresolved("192.0.2.3", 8081)),
                "https", List.of(InetSocketAddress.createUnresolved("192.0.2.3", 8443)))));
    }
}
