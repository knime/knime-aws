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

package org.knime.cloud.aws.dynamodb.batchget;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.knime.cloud.aws.dynamodb.BatchOperationResult;
import org.knime.cloud.aws.dynamodb.DynamicDataContainer;
import org.knime.cloud.aws.dynamodb.NodeConstants;
import org.knime.cloud.aws.dynamodb.utils.DynamoDBToKNIMEUtil;
import org.knime.cloud.aws.dynamodb.utils.DynamoDBUtil;
import org.knime.cloud.aws.dynamodb.utils.KNIMEToDynamoDBUtil;
import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowKey;
import org.knime.core.node.BufferedDataTable;
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

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes.Builder;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.utils.StringUtils;

/**
 * The {@code NodeModel} for the DynamoDB Batch Delete node.
 *
 * @author Alexander Fillbrunn, University of Konstanz
 */
final class DynamoDBBatchGetNodeModel extends NodeModel {

    private static final String CAPACITY_UNITS_FLOW_VAR = "batchDeleteConsumedCapacity";

    private final DynamoDBBatchGetSettings m_settings = new DynamoDBBatchGetSettings();

    /**
     * Default Constructor.
     */
    DynamoDBBatchGetNodeModel() {
        super(new PortType[] {AmazonConnectionInformationPortObject.TYPE, BufferedDataTable.TYPE},
                new PortType[] {AmazonConnectionInformationPortObject.TYPE, BufferedDataTable.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
    	return new PortObjectSpec[] {inSpecs[0], null};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final BufferedDataTable table = (BufferedDataTable)inObjects[1];
        final DataTableSpec inSpec = table.getDataTableSpec();
        final CloudConnectionInformation conInfo = inObjects[0] == null
                ? null : ((AmazonConnectionInformationPortObject)inObjects[0]).getConnectionInformation();
        final DynamoDbClient ddb = DynamoDBUtil.createClient(m_settings, conInfo);
        int nRetry = 0;
        final int hashIndex = inSpec.findColumnIndex(m_settings.getKeyColumns().getHashKeyColumn());
        final DataColumnSpec hashCol = inSpec.getColumnSpec(hashIndex);

        final int rangeIndex = m_settings.getKeyColumns().getRangeKeyColumn() == null
                            ? -1 : inSpec.findColumnIndex(m_settings.getKeyColumns().getRangeKeyColumn());
        final DataColumnSpec rangeCol = rangeIndex == -1 ? null : inSpec.getColumnSpec(rangeIndex);

        final DynamicDataContainer dc = new DynamicDataContainer(ds -> exec.createDataContainer(ds));

        final List<Map<String, AttributeValue>> batch = new ArrayList<>();
        double consumedCap = 0.0;
        long counter = 0;
        for (final DataRow row : table) {
            exec.checkCanceled();
            exec.setProgress((double)counter / table.size());
            final Map<String, AttributeValue> data = new HashMap<>();
            final DataCell hash = row.getCell(hashIndex);
            if (hash.isMissing()) {
                throw new InvalidSettingsException("The hash key column must not contain missing cells");
            }
            data.put(hashCol.getName(), KNIMEToDynamoDBUtil.cellToKeyAttributeValue(
                    hash, hashCol, m_settings.getKeyColumns().isHashKeyBinary()));

            if (rangeIndex != -1) {
                final DataCell range = row.getCell(rangeIndex);
                if (range.isMissing()) {
                    throw new InvalidSettingsException("The range key column must not contain missing cells");
                }
                data.put(rangeCol.getName(), KNIMEToDynamoDBUtil.cellToKeyAttributeValue(
                        range, rangeCol, m_settings.getKeyColumns().isRangeKeyBinary()));
            }
            batch.add(data);

            if (batch.size() == m_settings.getBatchSize()) {
                BatchGetItemResponse response;
                try {
                    // Send request
                    response = sendRequest(ddb, batch, nRetry);
                } catch (final ProvisionedThroughputExceededException e) {
                    throw new InvalidSettingsException(
                            NodeConstants.THROUGHPUT_ERROR, e);
                } catch (final ResourceNotFoundException e) {
                    throw new InvalidSettingsException(
                            String.format(NodeConstants.TABLE_MISSING_ERROR, m_settings.getTableName()), e);
                }
                // Get items and write them to the output
                counter = addResponseItemsToContainer(response, dc, counter);
                // Handle unprocessed keys and other information
                final BatchOperationResult res = handleResponse(response, batch);
                consumedCap += res.getConsumedCapacity();
                if (res.getNumUnprocessed() > 0) {
                    nRetry++;
                }
            }
        }

        // Clear the last items
        while (!batch.isEmpty()) {
            // Get response and handle items
            BatchGetItemResponse response;
            try {
                response = sendRequest(ddb, batch, nRetry);
            } catch (final ProvisionedThroughputExceededException e) {
                throw new InvalidSettingsException(NodeConstants.THROUGHPUT_ERROR, e);
            } catch (final ResourceNotFoundException e) {
                throw new InvalidSettingsException(
                        String.format(NodeConstants.TABLE_MISSING_ERROR, m_settings.getTableName()), e);
            }
            counter = addResponseItemsToContainer(response, dc, counter);
            // Handle unprocessed keys and other information
            final BatchOperationResult res = handleResponse(response, batch);
            consumedCap += res.getConsumedCapacity();
            if (res.getNumUnprocessed() > 0) {
                nRetry++;
            }
            // TODO: Reset tries to 0? Would probably end up throttling again anyways.
        }

        if (m_settings.publishConsumedCapUnits()) {
            pushFlowVariableDouble(CAPACITY_UNITS_FLOW_VAR, consumedCap);
        }
        dc.close();
        return new PortObject[] {inObjects[0], dc.getTable()};
    }

    private long addResponseItemsToContainer(final BatchGetItemResponse response,
            final DynamicDataContainer dc, final long counter) {
        final List<Map<String, AttributeValue>> items = response.responses().get(m_settings.getTableName());
        long count = counter;
        for (final Map<String, AttributeValue> item : items) {
            final HashMap<String, DataCell> cells = new HashMap<>();
            for (final Entry<String, AttributeValue> e : item.entrySet()) {
                cells.put(e.getKey(), DynamoDBToKNIMEUtil.attributeValueToDataCell(e.getValue()));
            }
            dc.addRow(new RowKey(String.format("Row%d", count++)), cells);
        }
        return count;
    }

    private BatchGetItemResponse sendRequest(final DynamoDbClient ddb, final List<Map<String, AttributeValue>> batch,
            final int nRetry) throws InterruptedException {
        // if previously not all items could be retrieved, we do exponential backoff
        if (nRetry > 0) {
            Thread.sleep((long)Math.pow(2, nRetry - 1) * 100);
        }
        final Map<String, KeysAndAttributes> req = new HashMap<>();
        final Builder kaBuilder = KeysAndAttributes.builder().keys(batch)
        .consistentRead(m_settings.isConsistentRead());
        if (!StringUtils.isBlank(m_settings.getProjectionExpression())) {
            kaBuilder.projectionExpression(m_settings.getProjectionExpression());
            if (!m_settings.getNames().isEmpty()) {
                kaBuilder.expressionAttributeNames(m_settings.getNames());
            }
        }
        req.put(m_settings.getTableName(), kaBuilder.build());

        final BatchGetItemRequest getItems = BatchGetItemRequest.builder().requestItems(req)
                .returnConsumedCapacity(m_settings.publishConsumedCapUnits()
                        ? ReturnConsumedCapacity.TOTAL : ReturnConsumedCapacity.NONE)
                .build();

        batch.clear();

        return ddb.batchGetItem(getItems);
    }

    private BatchOperationResult handleResponse(final BatchGetItemResponse response,
            final List<Map<String, AttributeValue>> batch) {
        // If the batch size is too large, we add items them to the beginning of next batch
        if (!response.unprocessedKeys().isEmpty()) {
            batch.addAll(response.unprocessedKeys().get(m_settings.getTableName()).keys());
        }

        double consumedCapacity = 0.0;
        if (m_settings.publishConsumedCapUnits()) {
            consumedCapacity = response.consumedCapacity().stream().mapToDouble(ConsumedCapacity::capacityUnits).sum();
        }

        final int numUnprocessed = response.unprocessedKeys().size();
        return new BatchOperationResult(numUnprocessed, consumedCapacity);
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
        final DynamoDBBatchGetSettings s = new DynamoDBBatchGetSettings();
        s.loadSettings(settings);
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
