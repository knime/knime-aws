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
package org.knime.cloud.aws.dynamodb.putitem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.knime.cloud.aws.dynamodb.DynamicDataContainer;
import org.knime.cloud.aws.dynamodb.NodeConstants;
import org.knime.cloud.aws.dynamodb.ValueMapping;
import org.knime.cloud.aws.dynamodb.utils.DynamoDBToKNIMEUtil;
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
import org.knime.core.util.Pair;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest.Builder;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.utils.StringUtils;

/**
 * The {@code NodeModel} for the DynamoDB Put Item node.
 *
 * @author Alexander Fillbrunn, University of Konstanz
 */
final class DynamoDBPutItemNodeModel extends NodeModel {

    private static final int EXPONENTIAL_BACKOFF_FACTOR = 100;
    private final DynamoDBPutItemSettings m_settings = new DynamoDBPutItemSettings();

    /**
     * Default Constructor.
     */
    DynamoDBPutItemNodeModel() {
        super(new PortType[] {
                AmazonConnectionInformationPortObject.TYPE, BufferedDataTable.TYPE},
                new PortType[] {AmazonConnectionInformationPortObject.TYPE, BufferedDataTable.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        // We can't know what fields we return
    	return new PortObjectSpec[] {inSpecs[0], null};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        final CloudConnectionInformation conInfo = inObjects[0] == null
                ? null : ((AmazonConnectionInformationPortObject)inObjects[0]).getConnectionInformation();
        final DynamoDbClient ddb = DynamoDBUtil.createClient(m_settings, conInfo);

        final BufferedDataTable inTable = (BufferedDataTable)inObjects[1];
        final DataTableSpec inSpec = inTable.getSpec();

        final List<Pair<ValueMapping, Integer>> columnValues = new ArrayList<>();
        final Map<String, AttributeValue> tmpValueMap = new HashMap<>();
        for (final ValueMapping vm : m_settings.getPlaceholders().getValues()) {
            if (!vm.isColumnType()) {
                tmpValueMap.put(vm.getName(), vm.getAttributeValue());
            } else {
                columnValues.add(new Pair<>(vm, inSpec.findColumnIndex(vm.getValue())));
            }
        }

        final String conditionExpression = m_settings.getConditionExpression();

        final ReturnConsumedCapacity returnCapacity = m_settings.publishConsumedCapUnits()
                ? ReturnConsumedCapacity.TOTAL : ReturnConsumedCapacity.NONE;

        final DynamicDataContainer dc = new DynamicDataContainer(ds -> exec.createDataContainer(ds));
        final Function<DataCell, AttributeValue>[] mappers = KNIMEToDynamoDBUtil.createMappers(inSpec);

        double consumedCap = 0.0;
        int nRetry = 0;
        double counter = 0.0;
        OUTER_LOOP:
        for (final DataRow inRow : inTable) {
            exec.setProgress(counter++ / inTable.size());
            exec.checkCanceled();
            // For those placeholders that insert the value of a column, we have to update the value map here
            Map<String, AttributeValue> valueMap = tmpValueMap;
            if (!columnValues.isEmpty()) {
                valueMap = new HashMap<>(valueMap);
                for (final Pair<ValueMapping, Integer> vm : columnValues) {
                    valueMap.put(vm.getFirst().getName(),
                            KNIMEToDynamoDBUtil.dataCellToAttributeValue(inRow.getCell(vm.getSecond())));
                }
            }

            final Builder builder = PutItemRequest.builder();
            if (!m_settings.getPlaceholders().getNames().isEmpty()) {
                builder.expressionAttributeNames(m_settings.getPlaceholders().getNames());
            }
            if (!m_settings.getPlaceholders().getValues().isEmpty()) {
                builder.expressionAttributeValues(valueMap);
            }
            if (!StringUtils.isBlank(conditionExpression)) {
                builder.conditionExpression(conditionExpression);
            }

            // Create map of data to write
            final Map<String, AttributeValue> data = new HashMap<>();
            for (int i = 0; i < inSpec.getNumColumns(); i++) {
                data.put(inSpec.getColumnSpec(i).getName(), mappers[i].apply(inRow.getCell(i)));
            }

            final PutItemRequest request = builder
                    .tableName(m_settings.getTableName())
                    .returnValues(m_settings.getReturnValue())
                    .returnConsumedCapacity(returnCapacity)
                    .item(data)
                    .build();

            PutItemResponse response = null;
            boolean success = false;
            while (!success) {
                exec.checkCanceled();
                if (nRetry > 0) {
                    Thread.sleep((int)Math.pow(2, nRetry - 1) * EXPONENTIAL_BACKOFF_FACTOR);
                }
                try {
                    response = ddb.putItem(request);
                    success = true;
                } catch (final ProvisionedThroughputExceededException e) {
                    nRetry++;
                } catch (final ConditionalCheckFailedException e) {
                    continue OUTER_LOOP;
                } catch (final ResourceNotFoundException e) {
                    throw new InvalidSettingsException(
                            String.format(NodeConstants.TABLE_MISSING_ERROR, m_settings.getTableName()), e);
                }
            }

            if (m_settings.publishConsumedCapUnits()) {
                consumedCap += response.consumedCapacity().capacityUnits();
            }

            if (m_settings.getReturnValue() != ReturnValue.NONE) {
                final Map<String, AttributeValue> attributes = response.attributes();
                final Map<String, DataCell> row = new HashMap<>();
                for (final Entry<String, AttributeValue> e : attributes.entrySet()) {
                    row.put(e.getKey(), DynamoDBToKNIMEUtil.attributeValueToDataCell(e.getValue()));
                }
                dc.addRow(inRow.getKey(), row);
            }
        }

        if (m_settings.publishConsumedCapUnits()) {
            pushFlowVariableDouble("putItemConsumedCapacityUnits", consumedCap);
        }

        dc.close();

        return new PortObject[] {inObjects[0], dc.getTable()};
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
        final DynamoDBPutItemSettings s = new DynamoDBPutItemSettings();
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
