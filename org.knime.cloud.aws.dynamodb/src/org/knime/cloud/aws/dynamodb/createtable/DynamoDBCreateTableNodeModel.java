/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 */
package org.knime.cloud.aws.dynamodb.createtable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.cloud.aws.dynamodb.createtable.IndexSettings.IndexType;
import org.knime.cloud.aws.dynamodb.utils.DynamoDBUtil;
import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest.Builder;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.SSESpecification;
import software.amazon.awssdk.services.dynamodb.model.SSEType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.StreamSpecification;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.Tag;
import software.amazon.awssdk.utils.StringUtils;

/**
 * The {@code NodeModel} for the DynamoDB Create Table node.
 *
 * @author Alexander Fillbrunn, University of Konstanz
 */
final class DynamoDBCreateTableNodeModel extends NodeModel {

    private static final String HASH_RANGE_SAME_ATTR_ERROR
        = "Hash and range key cannot be defined on the same attribute";

    private static final String RANGE_KEY_EMPTY_ERROR = "A range key must not have an empty name";

    private static final String HASH_KEY_EMPTY_ERROR = "A hash key must not have an empty name";

    /** Exponential backoff when waiting for table to become active is 2^ntries * EXP_BACKOFF_FACTOR. **/
    private static final int EXP_BACKOFF_FACTOR = 100;

    private final DynamoDBCreateTableSettings m_settings = new DynamoDBCreateTableSettings();

    /**
     * Default Constructor.
     */
    DynamoDBCreateTableNodeModel() {
        super(new PortType[] {
                AmazonConnectionInformationPortObject.TYPE_OPTIONAL,
                FlowVariablePortObject.TYPE_OPTIONAL},
                new PortType[0]);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[0];
    }

    private Projection getProjectionFromIndex(final IndexSettings idx) {
        final software.amazon.awssdk.services.dynamodb.model.Projection.Builder proj
            = Projection.builder().projectionType(idx.getProjectionType());

        if (idx.getProjectionType() == ProjectionType.INCLUDE) {
            proj.nonKeyAttributes(
                    idx.getProjection().stream()
                    .filter(s -> !StringUtils.isBlank(s)).collect(Collectors.toList()));
        }
        return proj.build();
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        final CloudConnectionInformation conInfo = inObjects[0] == null
                ? null : ((AmazonConnectionInformationPortObject)inObjects[0]).getConnectionInformation();

        final DynamoDbClient ddb = DynamoDBUtil.createClient(m_settings, conInfo);

        final Set<String> coveredAttrs = new HashSet<>();
        final List<AttributeDefinition> attrs = new ArrayList<>();
        final KeySchemaElement[] keys = new KeySchemaElement[m_settings.hasRangeKey() ? 2 : 1];

        attrs.add(AttributeDefinition.builder()
                    .attributeName(m_settings.getHashKeyName())
                    .attributeType(m_settings.getHashKeyType())
                    .build());
        coveredAttrs.add(m_settings.getHashKeyName());

        keys[0] = KeySchemaElement.builder()
                .attributeName(m_settings.getHashKeyName())
                .keyType(KeyType.HASH)
                .build();

        if (m_settings.hasRangeKey()) {
            attrs.add(AttributeDefinition.builder()
                    .attributeName(m_settings.getRangeKeyName())
                    .attributeType(m_settings.getRangeKeyType())
                    .build());
            coveredAttrs.add(m_settings.getRangeKeyName());
            keys[1] = KeySchemaElement.builder()
                    .attributeName(m_settings.getRangeKeyName())
                    .keyType(KeyType.RANGE)
                    .build();
        }

        final Tag[] tags = m_settings.getTags().toArray(new Tag[0]);
        final Builder builder = CreateTableRequest.builder()
        .tableName(m_settings.getTableName())
        .keySchema(keys)
        .tags(tags)
        .billingMode(m_settings.getBillingMode());

        if (m_settings.getBillingMode() == BillingMode.PROVISIONED) {
            builder.provisionedThroughput(ProvisionedThroughput.builder()
                    .readCapacityUnits((long)m_settings.getReadUnits())
                    .writeCapacityUnits((long)m_settings.getWriteUnits()).build());
        }

        final List<GlobalSecondaryIndex> globalIndexes = new ArrayList<>();
        final List<LocalSecondaryIndex> localIndexes = new ArrayList<>();

        createIndexes(coveredAttrs, attrs, globalIndexes, localIndexes);

        if (localIndexes.size() > 0) {
            builder.localSecondaryIndexes(localIndexes);
        }
        if (globalIndexes.size() > 0) {
            builder.globalSecondaryIndexes(globalIndexes);
        }
        if (m_settings.isSseEnabled()) {
            builder.sseSpecification(SSESpecification.builder()
                    .enabled(true)
                    .kmsMasterKeyId(m_settings.getKmsMasterKeyId())
                    .sseType(SSEType.KMS)
                    .build());
        }
        if (m_settings.isStreamsEnabled()) {
            builder.streamSpecification(StreamSpecification.builder()
                    .streamEnabled(true)
                    .streamViewType(m_settings.getStreamViewType())
                    .build());
        }

        final CreateTableRequest ctr = builder.attributeDefinitions(attrs).build();
        final CreateTableResponse response = ddb.createTable(ctr);
        int retry = 0;
        if (m_settings.isBlockUntilActive()) {
            TableDescription descr = response.tableDescription();
            while (descr.tableStatus() != TableStatus.ACTIVE) {
                exec.checkCanceled();
                final TableDescription d = descr;
                exec.setMessage(() -> String.format("Table \"%s\" has status \"%s\". Blocking until active...",
                        d.tableName(), d.tableStatus()));
                // exponential backoff until table is active
                Thread.sleep((long)Math.pow(2, retry++) * EXP_BACKOFF_FACTOR);
                descr = DynamoDBUtil.describeTable(m_settings, conInfo);
            }
        }

        return new PortObject[0];
    }

    private void createIndexes(final Set<String> coveredAttrs, final List<AttributeDefinition> attrs,
            final List<GlobalSecondaryIndex> globalIndexes, final List<LocalSecondaryIndex> localIndexes) {
        for (final IndexSettings idx : m_settings.getIndexes()) {
            if (idx.getType() == IndexType.GLOBAL) {
                final KeySchemaElement[] idxKeys = new KeySchemaElement[idx.hasRangeKey() ? 2 : 1];
                idxKeys[0] = KeySchemaElement.builder()
                        .attributeName(idx.getHashKeyName())
                        .keyType(KeyType.HASH)
                        .build();
                if (!coveredAttrs.contains(idx.getHashKeyName())) {
                    attrs.add(AttributeDefinition.builder()
                            .attributeName(idx.getHashKeyName())
                            .attributeType(idx.getHashKeyType())
                            .build());
                    coveredAttrs.add(idx.getHashKeyName());
                }

                if (idx.hasRangeKey()) {
                    idxKeys[1] = KeySchemaElement.builder()
                                 .attributeName(idx.getRangeKeyName())
                                 .keyType(KeyType.RANGE)
                                 .build();
                    if (!coveredAttrs.contains(idx.getRangeKeyName())) {
                        attrs.add(AttributeDefinition.builder()
                                .attributeName(idx.getRangeKeyName())
                                .attributeType(idx.getRangeKeyType())
                                .build());
                        coveredAttrs.add(idx.getRangeKeyName());
                    }
                }

                final ProvisionedThroughput pt = ProvisionedThroughput.builder()
                .readCapacityUnits((long)idx.getReadUnits())
                .writeCapacityUnits((long)idx.getWriteUnits())
                .build();

                final software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex.Builder gsiBuilder
                    = GlobalSecondaryIndex.builder()
                    .indexName(idx.getName())
                    .projection(getProjectionFromIndex(idx))
                    .keySchema(idxKeys);

                if (m_settings.getBillingMode() == BillingMode.PROVISIONED) {
                    gsiBuilder.provisionedThroughput(pt);
                }
                globalIndexes.add(gsiBuilder.build());
            } else {
                // A secondary index has the same hash key as the parent table
                final KeySchemaElement hKey = KeySchemaElement.builder()
                        .attributeName(m_settings.getHashKeyName())
                        .keyType(KeyType.HASH)
                        .build();

                final KeySchemaElement rKey = KeySchemaElement.builder()
                        .attributeName(idx.getRangeKeyName())
                        .keyType(KeyType.RANGE)
                        .build();
                if (!coveredAttrs.contains(idx.getRangeKeyName())) {
                    attrs.add(AttributeDefinition.builder()
                            .attributeName(idx.getRangeKeyName())
                            .attributeType(idx.getRangeKeyType())
                            .build());
                    coveredAttrs.add(idx.getRangeKeyName());
                }

                localIndexes.add(
                    LocalSecondaryIndex.builder()
                    .indexName(idx.getName())
                    .projection(getProjectionFromIndex(idx))
                    .keySchema(hKey, rKey)
                    .build());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        final DynamoDBCreateTableSettings s = new DynamoDBCreateTableSettings();
        s.loadSettings(settings);

        if (StringUtils.isBlank(s.getHashKeyName())) {
            throw new InvalidSettingsException(HASH_KEY_EMPTY_ERROR);
        }

        final Map<String, ScalarAttributeType> types = new HashMap<>();
        types.put(s.getHashKeyName(), s.getHashKeyType());
        if (s.hasRangeKey()) {
            if (StringUtils.isBlank(s.getRangeKeyName())) {
                throw new InvalidSettingsException(RANGE_KEY_EMPTY_ERROR);
            }
            if (s.getRangeKeyName().equals(s.getHashKeyName())) {
                throw new InvalidSettingsException(HASH_RANGE_SAME_ATTR_ERROR);
            }
            types.put(s.getRangeKeyName(), s.getRangeKeyType());
        }

        // Check if attributes with same name and different types are entered as indexes
        for (final IndexSettings idx : s.getIndexes()) {
            if (StringUtils.isBlank(idx.getName())) {
                throw new InvalidSettingsException("An index must not have an empty name");
            }
            if (idx.getType() == IndexType.GLOBAL && StringUtils.isBlank(idx.getHashKeyName())) {
                throw new InvalidSettingsException(HASH_KEY_EMPTY_ERROR);
            }
            final ScalarAttributeType existingHash = types.get(idx.getHashKeyName());
            if (existingHash != null && existingHash != idx.getHashKeyType()) {
                throw new InvalidSettingsException(String.format("Index %s defines a hash key named %s with type %s "
                        + "but an attribute with that name and type %s was already registered",
                        idx.getName(), DynamoDBUtil.keyTypeToHumanReadable(idx.getHashKeyType()),
                        DynamoDBUtil.keyTypeToHumanReadable(existingHash)));
            }
            types.put(idx.getHashKeyName(), idx.getHashKeyType());

            if (idx.hasRangeKey() || idx.getType() == IndexType.LOCAL) {
                if (StringUtils.isBlank(idx.getRangeKeyName())) {
                    throw new InvalidSettingsException(RANGE_KEY_EMPTY_ERROR);
                }
                if (idx.getRangeKeyName().equals(idx.getHashKeyName())) {
                    throw new InvalidSettingsException(HASH_RANGE_SAME_ATTR_ERROR);
                }
                final ScalarAttributeType existingRange = types.get(idx.getRangeKeyName());
                if (existingRange != null && existingRange != idx.getRangeKeyType()) {
                    throw new InvalidSettingsException(String.format("Index %s defines a range key named %s"
                            + " with type %s but an attribute with that name and type %s was already registered",
                            idx.getName(), DynamoDBUtil.keyTypeToHumanReadable(idx.getRangeKeyType()),
                            DynamoDBUtil.keyTypeToHumanReadable(existingRange)));
                }
                types.put(idx.getRangeKeyName(), idx.getRangeKeyType());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.loadSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
    }
}
