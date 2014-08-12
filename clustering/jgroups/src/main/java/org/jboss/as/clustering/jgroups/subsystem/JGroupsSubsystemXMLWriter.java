/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.clustering.jgroups.subsystem;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Paul Ferraro
 * @author Richard Achmatowicz (c) 2011 Red Hat Inc.
 * @author Tristan Tarrant
 */
public class JGroupsSubsystemXMLWriter implements XMLElementWriter<SubsystemMarshallingContext> {
    /**
     * {@inheritDoc}
     * @see org.jboss.staxmapper.XMLElementWriter#writeContent(org.jboss.staxmapper.XMLExtendedStreamWriter, java.lang.Object)
     */
    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(JGroupsSchema.CURRENT.getNamespaceUri(), false);
        ModelNode model = context.getModelNode();

        if (model.isDefined()) {
            if (model.hasDefined(ChannelResourceDefinition.WILDCARD_PATH.getKey())) {
                writer.writeStartElement(Element.CHANNELS.getLocalName());
                JGroupsSubsystemResourceDefinition.DEFAULT_CHANNEL.marshallAsAttribute(model, writer);
                for (Property property: model.get(ChannelResourceDefinition.WILDCARD_PATH.getKey()).asPropertyList()) {
                    writer.writeStartElement(Element.CHANNEL.getLocalName());
                    writer.writeAttribute(Attribute.NAME.getLocalName(), property.getName());
                    ModelNode channel = property.getValue();
                    ChannelResourceDefinition.STACK.marshallAsAttribute(channel, writer);
                    ChannelResourceDefinition.MODULE.marshallAsAttribute(channel, writer);
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
            if (model.hasDefined(StackResourceDefinition.WILDCARD_PATH.getKey())) {
                writer.writeStartElement(Element.STACKS.getLocalName());
                JGroupsSubsystemResourceDefinition.DEFAULT_STACK.marshallAsAttribute(model, writer);
                for (Property property: model.get(StackResourceDefinition.WILDCARD_PATH.getKey()).asPropertyList()) {
                    writer.writeStartElement(Element.STACK.getLocalName());
                    writer.writeAttribute(Attribute.NAME.getLocalName(), property.getName());
                    ModelNode stack = property.getValue();
                    if (stack.get(TransportResourceDefinition.PATH.getKeyValuePair()).isDefined()) {
                        writeTransport(writer, stack.get(TransportResourceDefinition.PATH.getKeyValuePair()));
                    }
                    // write the protocols in their correct order
                    if (stack.hasDefined(ProtocolResourceDefinition.WILDCARD_PATH.getKey())) {
                        for (Property protocol: StackAddHandler.getOrderedProtocolPropertyList(stack)) {
                            writeProtocol(writer, protocol.getValue());
                        }
                    }
                    if (stack.get(RelayResourceDefinition.PATH.getKeyValuePair()).isDefined()) {
                        writeRelay(writer, stack.get(RelayResourceDefinition.PATH.getKeyValuePair()));
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
    }

    private static void writeTransport(XMLExtendedStreamWriter writer, ModelNode transport) throws XMLStreamException {
        writer.writeStartElement(Element.TRANSPORT.getLocalName());
        writeProtocolAttributes(writer, transport);
        TransportResourceDefinition.SHARED.marshallAsAttribute(transport, writer);
        TransportResourceDefinition.DIAGNOSTICS_SOCKET_BINDING.marshallAsAttribute(transport, writer);
        TransportResourceDefinition.DEFAULT_EXECUTOR.marshallAsAttribute(transport, writer);
        TransportResourceDefinition.OOB_EXECUTOR.marshallAsAttribute(transport, writer);
        TransportResourceDefinition.TIMER_EXECUTOR.marshallAsAttribute(transport, writer);
        TransportResourceDefinition.THREAD_FACTORY.marshallAsAttribute(transport, writer);
        TransportResourceDefinition.MACHINE.marshallAsAttribute(transport, writer);
        TransportResourceDefinition.RACK.marshallAsAttribute(transport, writer);
        TransportResourceDefinition.SITE.marshallAsAttribute(transport, writer);
        writeProtocolElements(writer, transport);
        writeThreadFactoryElements(writer, transport);
        writeExecutorElements(Element.DEFAULT_EXECUTOR, ModelKeys.DEFAULT_EXECUTOR, writer, transport);
        writeExecutorElements(Element.OOB_EXECUTOR, ModelKeys.OOB_EXECUTOR, writer, transport);
        writeExecutorElements(Element.TIMER_EXECUTOR, ModelKeys.TIMER_EXECUTOR, writer, transport);
        writer.writeEndElement();
    }

    private static void writeThreadFactoryElements(XMLExtendedStreamWriter writer, ModelNode transport) throws XMLStreamException {
        if (transport.hasDefined(ThreadFactoryResourceDefinition.PATH.getKey())) {
            ModelNode threadFactory = transport.get(ThreadFactoryResourceDefinition.PATH.getKeyValuePair());
            writer.writeStartElement(Element.THREAD_FACTORY.getLocalName());
            ThreadFactoryResourceDefinition.THREAD_NAME_PATTERN.marshallAsAttribute(threadFactory, writer);
            ThreadFactoryResourceDefinition.GROUP_NAME.marshallAsAttribute(threadFactory, writer);
            ThreadFactoryResourceDefinition.PRIORITY.marshallAsAttribute(threadFactory, writer);
            writer.writeEndElement();
        }
    }

    private static void writeExecutorElements(Element element, String modelPath, XMLExtendedStreamWriter writer, ModelNode transport) throws XMLStreamException {
        if (transport.hasDefined(ExecutorResourceDefinition.pathElement(modelPath).getKey())) {
            ModelNode executor = transport.get(ExecutorResourceDefinition.pathElement(modelPath).getKeyValuePair());
            writer.writeStartElement(element.getLocalName());
            CommonThreadAttributeDefinitions.CORE_THREADS.marshallAsAttribute(executor, writer);
            CommonThreadAttributeDefinitions.QUEUE_LENGTH.marshallAsAttribute(executor, writer);
            CommonThreadAttributeDefinitions.MAX_THREADS.marshallAsAttribute(executor, writer);
            CommonThreadAttributeDefinitions.KEEPALIVE_TIME.marshallAsAttribute(executor, writer);
            CommonThreadAttributeDefinitions.KEEPALIVE_TIME_UNIT.marshallAsAttribute(executor, writer);
            CommonThreadAttributeDefinitions.ALLOW_CORE_TIMEOUT.marshallAsAttribute(executor, writer);
            writer.writeEndElement();
        }
    }

    private static void writeProtocol(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {
        writer.writeStartElement(Element.PROTOCOL.getLocalName());
        writeProtocolAttributes(writer, protocol);
        writeProtocolElements(writer, protocol);
        writer.writeEndElement();
    }

    private static void writeProtocolAttributes(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {
        ProtocolResourceDefinition.TYPE.marshallAsAttribute(protocol, writer);
        ProtocolResourceDefinition.SOCKET_BINDING.marshallAsAttribute(protocol, writer);
        ProtocolResourceDefinition.MODULE.marshallAsAttribute(protocol, writer);
    }

    private static void writeProtocolElements(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {
        writeProtocolProperties(writer, protocol);
    }

    private static void writeProtocolProperties(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {
        // the format of the property elements
        //  "property" => {
        //       "relative-to" => {"value" => "fred"},
        //   }
        if (protocol.hasDefined(PropertyResourceDefinition.WILDCARD_PATH.getKey())) {
            for (Property property: protocol.get(PropertyResourceDefinition.WILDCARD_PATH.getKey()).asPropertyList()) {
                writer.writeStartElement(Element.PROPERTY.getLocalName());
                writer.writeAttribute(Attribute.NAME.getLocalName(), property.getName());
                Property complexValue = property.getValue().asProperty();
                writer.writeCharacters(complexValue.getValue().asString());
                writer.writeEndElement();
            }
        }
    }

    private static void writeRelay(XMLExtendedStreamWriter writer, ModelNode relay) throws XMLStreamException {
        writer.writeStartElement(Element.RELAY.getLocalName());
        RelayResourceDefinition.SITE.marshallAsAttribute(relay, writer);
        if (relay.hasDefined(RemoteSiteResourceDefinition.WILDCARD_PATH.getKey())) {
            for (Property property: relay.get(RemoteSiteResourceDefinition.WILDCARD_PATH.getKey()).asPropertyList()) {
                writer.writeStartElement(Element.REMOTE_SITE.getLocalName());
                writer.writeAttribute(Attribute.NAME.getLocalName(), property.getName());
                ModelNode remoteSite = property.getValue();
                RemoteSiteResourceDefinition.CHANNEL.marshallAsAttribute(remoteSite, writer);
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
    }
}
