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
package org.knime.cloud.aws.dynamodb.scan;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.knime.cloud.aws.dynamodb.DynamicDataContainer;
import org.knime.cloud.aws.dynamodb.NodeConstants;
import org.knime.cloud.aws.dynamodb.ValueMapping;
import org.knime.cloud.aws.dynamodb.utils.DynamoDBToKNIMEUtil;
import org.knime.cloud.aws.dynamodb.utils.DynamoDBUtil;
import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.data.DataCell;
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
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest.Builder;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.paginators.ScanIterable;

/**
 * The {@code NodeModel} for the DynamoDB Scan node.
 *
 * @author Alexander Fillbrunn, University of Konstanz
 */
final class DynamoDBScanNodeModel extends NodeModel {

    private final DynamoDBScanSettings m_settings = new DynamoDBScanSettings();

    /**
     * Default Constructor.
     */
    DynamoDBScanNodeModel() {
        super(new PortType[] {AmazonConnectionInformationPortObject.TYPE},
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

        // Setup maps for placeholders in the expressions. DynamoDB requires this for reserved keywords
        // and strange column names. We always use it to be sure.
        final Map<String, AttributeValue> valueMap = new HashMap<>();
        for (final ValueMapping vm : m_settings.getPlaceholderSettings().getValues()) {
            valueMap.put(vm.getName(), vm.getAttributeValue());
        }

        final Builder builder = ScanRequest.builder()
            .returnConsumedCapacity(m_settings.publishConsumedCapUnits()
                                    ? ReturnConsumedCapacity.TOTAL : ReturnConsumedCapacity.NONE)
            .tableName(m_settings.getTableName())
            .consistentRead(m_settings.isConsistentRead());

        final boolean hasFilter = m_settings.getFilterExpr().trim().length() > 0;
        final boolean hasProjection = m_settings.getProjectionExpr().trim().length() > 0;

        if (hasFilter) {
            builder.filterExpression(m_settings.getFilterExpr());
        }
        if (hasProjection) {
            builder.projectionExpression(m_settings.getProjectionExpr());
        }

        if (hasFilter || hasProjection) {
            if (!m_settings.getPlaceholderSettings().getNames().isEmpty()) {
                builder.expressionAttributeNames(m_settings.getPlaceholderSettings().getNames());
            }
            if (!valueMap.isEmpty()) {
                builder.expressionAttributeValues(valueMap);
            }
        }

        if (m_settings.getUseIndex()) {
            builder.indexName(m_settings.getIndexName());
        }
        if (m_settings.getLimit() > 0) {
            builder.limit(m_settings.getLimit());
        }

        ScanIterable pages;
        try {
            pages = ddb.scanPaginator(builder.build());
        } catch (final ResourceNotFoundException e) {
            final String msg = m_settings.getUseIndex()
                    ? String.format(NodeConstants.TABLE_OR_INDEX_MISSING_ERROR,
                            m_settings.getTableName(), m_settings.getIndexName())
                    : String.format(NodeConstants.TABLE_MISSING_ERROR, m_settings.getTableName());
            throw new InvalidSettingsException(msg, e);
        }

        final DynamicDataContainer dc = new DynamicDataContainer(ds -> exec.createDataContainer(ds));

        double consumedCap = 0.0;
        long rowCount = 0;
        OUTER_LOOP:
        for (final ScanResponse response : pages) {
            // In the first iteration we build the table schema and create the data container
            if (m_settings.publishConsumedCapUnits()) {
                consumedCap += response.consumedCapacity().capacityUnits();
            }
            for (final Map<String, AttributeValue> item : response.items()) {
                exec.checkCanceled();
                if (m_settings.getLimit() > 0 && rowCount == m_settings.getLimit()) {
                    break OUTER_LOOP;
                }
                exec.checkCanceled();
                final Map<String, DataCell> cells = new HashMap<>();
                for (final Entry<String, AttributeValue> e : item.entrySet()) {
                    cells.put(e.getKey(), DynamoDBToKNIMEUtil.attributeValueToDataCell(e.getValue()));
                }
                dc.addRow(new RowKey(String.format("Row%d", rowCount++)), cells);
            }
        }
        dc.close();

        if (m_settings.publishConsumedCapUnits()) {
            pushFlowVariableDouble("scanConsumedCapacityUnits", consumedCap);
        }

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
        final DynamoDBScanSettings s = new DynamoDBScanSettings();
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
