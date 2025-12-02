/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.controller;

import java.time.Duration;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.subsystem.resource.ResourceModelResolver;

/**
 * Attribute definition for ISO 8601 duration strings that resolves to a {@link Duration}.
 *
 * @author Radoslav Husar
 * @see <a href="https://www.iso.org/obp/ui/#iso:std:iso:8601:-1:ed-1:v1:en">ISO 8601</a>
 */
public class ISO8601DurationAttributeDefinition extends SimpleAttributeDefinition implements ResourceModelResolver<Duration> {

    ISO8601DurationAttributeDefinition(Builder builder) {
        super(builder);
    }

    @Override
    public Duration resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        String value = this.resolveModelAttribute(context, model).asStringOrNull();
        return (value != null) ? Duration.parse(value) : null;
    }

    public static class Builder extends AbstractAttributeDefinitionBuilder<Builder, ISO8601DurationAttributeDefinition> {

        public Builder(String attributeName) {
            super(attributeName, ModelType.STRING);
            this.setAllowExpression(true);
            this.setFlags(Flag.RESTART_RESOURCE_SERVICES);
            this.setValidator((parameterName, value) -> {
                if (value.isDefined()) {
                    String stringValue = value.asString();
                    try {
                        Duration.parse(stringValue);
                    } catch (Exception e) {
                        throw new OperationFailedException("Invalid ISO 8601 duration format for " + parameterName + ": " + stringValue, e);
                    }
                }
            });
        }

        public Builder(String attributeName, ISO8601DurationAttributeDefinition basis) {
            super(attributeName, basis);
        }

        public Builder setDefaultValue(Duration duration) {
            if (duration != null) {
                this.setRequired(false);
                this.setDefaultValue(new ModelNode(duration.toString()));
            }
            return this;
        }

        @Override
        public ISO8601DurationAttributeDefinition build() {
            return new ISO8601DurationAttributeDefinition(this);
        }
    }
}
