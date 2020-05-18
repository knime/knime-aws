/*
 * ------------------------------------------------------------------------
 *
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
 * ---------------------------------------------------------------------
 *
 * History
 *   1 Jul 2019 (Alexander): created
 */
package org.knime.cloud.aws.dynamodb.batchput;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.knime.cloud.aws.dynamodb.BatchOperationResult;
import org.knime.cloud.aws.dynamodb.NodeConstants;
import org.knime.cloud.aws.dynamodb.utils.DynamoDBUtil;
import org.knime.cloud.aws.dynamodb.utils.KNIMEToDynamoDBUtil;
import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
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
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * The {@code NodeModel} for the DynamoDB query node.
 *
 * @author Alexander Fillbrunn, University of Konstanz
 */
final class DynamoDBBatchPutNodeModel extends NodeModel {

    private static final String CAPACITY_UNITS_FLOW_VAR = "batchPutConsumedCapacity";
    
    private DynamoDBBatchPutSettings m_settings = new DynamoDBBatchPutSettings();

    /**
     * Default Constructor.
     */
    DynamoDBBatchPutNodeModel() {
        super(new PortType[] {AmazonConnectionInformationPortObject.TYPE_OPTIONAL, BufferedDataTable.TYPE},
                new PortType[0]);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[0];
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        BufferedDataTable table = (BufferedDataTable)inObjects[1];
        DataTableSpec inSpec = table.getDataTableSpec();
        
        CloudConnectionInformation conInfo = inObjects[0] == null
                ? null : ((AmazonConnectionInformationPortObject)inObjects[0]).getConnectionInformation();
        DynamoDbClient ddb = DynamoDBUtil.createClient(m_settings, conInfo);
        int nRetry = 0;
        Function<DataCell, AttributeValue>[] mappers = KNIMEToDynamoDBUtil.createMappers(inSpec);
        
        List<WriteRequest> batch = new ArrayList<>();
        double consumedCap = 0.0;
        double count = 0;
        for (DataRow row : table) {
            exec.checkCanceled();
            exec.setProgress(count++ / table.size());
            Map<String, AttributeValue> data = new HashMap<>();
            for (int i = 0; i < inSpec.getNumColumns(); i++) {
                data.put(inSpec.getColumnSpec(i).getName(), mappers[i].apply(row.getCell(i)));
            }
            batch.add(WriteRequest.builder().putRequest(PutRequest.builder().item(data).build()).build());
            if (batch.size() == m_settings.getBatchSize()) {
                BatchOperationResult res;
                try {
                    res = sendBatch(ddb, batch, nRetry);
                } catch (ProvisionedThroughputExceededException e) {
                    throw new InvalidSettingsException(NodeConstants.THROUGHPUT_ERROR, e);
                } catch (ResourceNotFoundException e) {
                    throw new InvalidSettingsException(
                            String.format(NodeConstants.TABLE_MISSING_ERROR, m_settings.getTableName()), e);
                }
                consumedCap += res.getConsumedCapacity();
                if (res.getNumUnprocessed() > 0) {
                    nRetry++;
                }
            }
        }
        
        if (!batch.isEmpty()) {
            BatchOperationResult res;
            try {
                res = sendBatch(ddb, batch, nRetry);
            } catch (ProvisionedThroughputExceededException e) {
                throw new InvalidSettingsException(NodeConstants.THROUGHPUT_ERROR, e);
            } catch (ResourceNotFoundException e) {
                throw new InvalidSettingsException(
                        String.format(NodeConstants.TABLE_MISSING_ERROR, m_settings.getTableName()), e);
            }
            consumedCap += res.getConsumedCapacity();
            if (res.getNumUnprocessed() > 0) {
                nRetry++;
            }
        }
        
        if (m_settings.publishConsumedCapUnits()) {
            pushFlowVariableDouble(CAPACITY_UNITS_FLOW_VAR, consumedCap);
        }
        
        return new PortObject[0];
    }
    
    private BatchOperationResult sendBatch(final DynamoDbClient ddb, final List<WriteRequest> batch, final int nRetry)
            throws InterruptedException {
        // if previously not all items could be written, we do exponential backoff
        if (nRetry > 0) {
            Thread.sleep((long)Math.pow(2, nRetry - 1) * 100);
        }
        Map<String, List<WriteRequest>> req = new HashMap<>();
        req.put(m_settings.getTableName(), batch);
        BatchWriteItemResponse response = ddb.batchWriteItem(
                BatchWriteItemRequest.builder()
                .requestItems(req)
                .returnConsumedCapacity(m_settings.publishConsumedCapUnits()
                                        ? ReturnConsumedCapacity.TOTAL : ReturnConsumedCapacity.NONE)
                .build());
        batch.clear();

        // If the batch size is too large, we add items them to the beginning of next batch
        if (!response.unprocessedItems().isEmpty()) {
            batch.addAll(response.unprocessedItems().get(m_settings.getTableName()));
        }
        
        double consumedCapacity = 0.0;
        if (m_settings.publishConsumedCapUnits()) {
            consumedCapacity = response.consumedCapacity().stream().mapToDouble(c -> c.capacityUnits()).sum();
        }
        
        int numUnprocessed = response.unprocessedItems().size();
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
        DynamoDBBatchPutSettings s = new DynamoDBBatchPutSettings();
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
