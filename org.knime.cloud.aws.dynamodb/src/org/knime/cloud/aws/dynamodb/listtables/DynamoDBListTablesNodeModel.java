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
package org.knime.cloud.aws.dynamodb.listtables;

import java.io.File;
import java.io.IOException;

import org.knime.cloud.aws.dynamodb.utils.DynamoDBUtil;
import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.DataContainer;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.StringCell;
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
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest.Builder;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.dynamodb.paginators.ListTablesIterable;

/**
 * The {@code NodeModel} for the DynamoDB List Tables node.
 *
 * @author Alexander Fillbrunn, University of Konstanz
 */
final class DynamoDBListTablesNodeModel extends NodeModel {

    private final DynamoDBListTablesSettings m_settings = new DynamoDBListTablesSettings();

    /**
     * Default Constructor.
     */
    DynamoDBListTablesNodeModel() {
        super(new PortType[] {AmazonConnectionInformationPortObject.TYPE},
                new PortType[] {AmazonConnectionInformationPortObject.TYPE, BufferedDataTable.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[] {inSpecs[0], createOutputSpec()};
    }

    private DataTableSpec createOutputSpec() {
        final DataTableSpecCreator specCreator = new DataTableSpecCreator();
        specCreator.addColumns(new DataColumnSpecCreator("tableName", StringCell.TYPE).createSpec());
        return specCreator.createSpec();
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        final CloudConnectionInformation con = inObjects[0] == null
                ? null : ((AmazonConnectionInformationPortObject)inObjects[0]).getConnectionInformation();

        final DynamoDbClient ddb = DynamoDBUtil.createClient(m_settings, con);

        final Builder builder = ListTablesRequest.builder();
        if (m_settings.getLimit() != DynamoDBListTablesSettings.UNLIMITED) {
            builder.limit(m_settings.getLimit());
        }

        final ListTablesIterable tables = ddb.listTablesPaginator(builder.build());

        final DataContainer dc = exec.createDataContainer(createOutputSpec());

        long counter = 0;
        OUTER_LOOP:
        for (final ListTablesResponse response : tables) {
            exec.checkCanceled();
            for (final String tableName : response.tableNames()) {
                dc.addRowToTable(new DefaultRow(
                        new RowKey(String.format("Row%d", counter++)), new StringCell(tableName)));
                if (m_settings.getLimit() != DynamoDBListTablesSettings.UNLIMITED && counter == m_settings.getLimit()) {
                    break OUTER_LOOP;
                }
            }
        }
        dc.close();
        return new PortObject[] {inObjects[0], (BufferedDataTable)dc.getTable()};
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
        final DynamoDBListTablesSettings s = new DynamoDBListTablesSettings();
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
