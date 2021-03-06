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

package org.jboss.as.clustering.infinispan.subsystem;

import org.jboss.as.clustering.controller.Operations;
import org.jboss.as.clustering.controller.transform.LegacyPropertyMapGetOperationTransformer;
import org.jboss.as.clustering.controller.transform.LegacyPropertyResourceTransformer;
import org.jboss.as.clustering.controller.transform.LegacyPropertyWriteOperationTransformer;
import org.jboss.as.clustering.controller.transform.SimpleOperationTransformer;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.global.MapOperations;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;

/**
 * Resource description for the addressable resource and its alias
 *
 * /subsystem=infinispan/cache-container=X/cache=Y/store=mixed-jdbc
 * /subsystem=infinispan/cache-container=X/cache=Y/mixed-keyed-jdbc-store=MIXED_KEYED_JDBC_STORE
 *
 * @author Richard Achmatowicz (c) 2011 Red Hat Inc.
 * @author Paul Ferraro
 */
public class MixedKeyedJDBCStoreResourceDefinition extends JDBCStoreResourceDefinition {

    static final PathElement LEGACY_PATH = PathElement.pathElement("mixed-keyed-jdbc-store", "MIXED_KEYED_JDBC_STORE");
    static final PathElement PATH = pathElement("mixed-jdbc");

    @Deprecated
    enum DeprecatedAttribute implements org.jboss.as.clustering.controller.Attribute {
        BINARY_TABLE(BinaryKeyedJDBCStoreResourceDefinition.DeprecatedAttribute.TABLE),
        STRING_TABLE(StringKeyedJDBCStoreResourceDefinition.DeprecatedAttribute.TABLE),
        ;
        private final AttributeDefinition definition;

        DeprecatedAttribute(org.jboss.as.clustering.controller.Attribute attribute) {
            this.definition = attribute.getDefinition();
        }

        @Override
        public AttributeDefinition getDefinition() {
            return this.definition;
        }
    }

    static void buildTransformation(ModelVersion version, ResourceTransformationDescriptionBuilder parent) {
        ResourceTransformationDescriptionBuilder builder = InfinispanModel.VERSION_4_0_0.requiresTransformation(version) ? parent.addChildRedirection(PATH, LEGACY_PATH) : parent.addChildResource(PATH);

        BinaryTableResourceDefinition.buildTransformation(version, builder);
        StringTableResourceDefinition.buildTransformation(version, builder);

        JDBCStoreResourceDefinition.buildTransformation(version, builder);

        if (InfinispanModel.VERSION_4_0_0.requiresTransformation(version)) {
            builder.setCustomResourceTransformer(new ResourceTransformer() {
                @Override
                public void transformResource(ResourceTransformationContext context, PathAddress address, Resource resource) throws OperationFailedException {
                    final ModelNode model = resource.getModel();

                    final ModelNode binaryTableModel = Resource.Tools.readModel(resource.removeChild(BinaryTableResourceDefinition.PATH));
                    if (binaryTableModel != null && binaryTableModel.isDefined()) {
                        model.get(DeprecatedAttribute.BINARY_TABLE.getName()).set(binaryTableModel);
                    }

                    final ModelNode stringTableModel = Resource.Tools.readModel(resource.removeChild(StringTableResourceDefinition.PATH));
                    if (stringTableModel != null && stringTableModel.isDefined()) {
                        model.get(DeprecatedAttribute.STRING_TABLE.getName()).set(stringTableModel);
                    }

                    final ModelNode properties = model.remove(StoreResourceDefinition.Attribute.PROPERTIES.getName());
                    final ResourceTransformationContext childContext = context.addTransformedResource(PathAddress.EMPTY_ADDRESS, resource);

                    LegacyPropertyResourceTransformer.transformPropertiesToChildrenResources(properties, address, childContext);

                    context.processChildren(resource);
                }
            });
        }

        if (InfinispanModel.VERSION_3_0_0.requiresTransformation(version)) {
            builder.addRawOperationTransformationOverride(MapOperations.MAP_GET_DEFINITION.getName(), new SimpleOperationTransformer(new LegacyPropertyMapGetOperationTransformer()));
            for (String opName : Operations.getAllWriteAttributeOperationNames()) {
                builder.addOperationTransformationOverride(opName)
                        .inheritResourceAttributeDefinitions()
                        .setCustomOperationTransformer(new LegacyPropertyWriteOperationTransformer());
            }
        }
    }

    MixedKeyedJDBCStoreResourceDefinition(boolean allowRuntimeOnlyRegistration) {
        super(PATH, LEGACY_PATH, new InfinispanResourceDescriptionResolver(PATH, pathElement("jdbc"), WILDCARD_PATH), allowRuntimeOnlyRegistration, descriptor -> descriptor
                .addExtraParameters(DeprecatedAttribute.class)
                .addRequiredChildren(BinaryTableResourceDefinition.PATH, StringTableResourceDefinition.PATH)
                // Translate deprecated BINARY_TABLE attribute into separate add table operation
                .addOperationTranslator(new TableAttributeTranslator(DeprecatedAttribute.BINARY_TABLE, BinaryTableResourceDefinition.PATH))
                // Translate deprecated STRING_TABLE attribute into separate add table operation
                .addOperationTranslator(new TableAttributeTranslator(DeprecatedAttribute.STRING_TABLE, StringTableResourceDefinition.PATH))
            , address -> new MixedKeyedJDBCStoreBuilder(address.getParent()), registration -> {
                registration.registerReadWriteAttribute(DeprecatedAttribute.BINARY_TABLE.getDefinition(), BinaryKeyedJDBCStoreResourceDefinition.LEGACY_READ_TABLE_HANDLER, BinaryKeyedJDBCStoreResourceDefinition.LEGACY_WRITE_TABLE_HANDLER);
                registration.registerReadWriteAttribute(DeprecatedAttribute.STRING_TABLE.getDefinition(), StringKeyedJDBCStoreResourceDefinition.LEGACY_READ_TABLE_HANDLER, StringKeyedJDBCStoreResourceDefinition.LEGACY_WRITE_TABLE_HANDLER);

                new BinaryTableResourceDefinition().register(registration);
                new StringTableResourceDefinition().register(registration);
            });
    }
}
