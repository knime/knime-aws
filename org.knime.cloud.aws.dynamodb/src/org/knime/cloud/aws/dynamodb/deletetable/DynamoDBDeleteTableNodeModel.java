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
package org.knime.cloud.aws.dynamodb.deletetable;

import java.io.File;
import java.io.IOException;

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
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * The {@code NodeModel} for the DynamoDB Delete Table node.
 *
 * @author Alexander Fillbrunn, University of Konstanz
 */
final class DynamoDBDeleteTableNodeModel extends NodeModel {

    /** Exponential backoff when waiting for table to become active is 2^ntries * EXP_BACKOFF_FACTOR. **/
    private static final int EXP_BACKOFF_FACTOR = 100;

    private final DynamoDBDeleteTableSettings m_settings = new DynamoDBDeleteTableSettings();

    /**
     * Default Constructor.
     */
    DynamoDBDeleteTableNodeModel() {
        super(new PortType[] {
                AmazonConnectionInformationPortObject.TYPE,
                FlowVariablePortObject.TYPE_OPTIONAL},
                new PortType[] {AmazonConnectionInformationPortObject.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[] {inSpecs[0]};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        final CloudConnectionInformation conInfo = inObjects[0] == null
                ? null : ((AmazonConnectionInformationPortObject)inObjects[0]).getConnectionInformation();

        final DynamoDbClient ddb = DynamoDBUtil.createClient(m_settings, conInfo);

        final DeleteTableRequest dtr = DeleteTableRequest.builder().tableName(m_settings.getTableName()).build();
        DeleteTableResponse response;
        try {
            response = ddb.deleteTable(dtr);
        } catch (final ResourceNotFoundException e) {
            throw new InvalidSettingsException(
                    String.format("The given table \"%s\" does not exist.", m_settings.getTableName()), e);
        }
        int retry = 0;
        if (m_settings.isBlockUntilDeleted()) {
            TableDescription descr = response.tableDescription();
            while (descr != null) {
                exec.checkCanceled();
                final TableDescription d = descr;
                exec.setMessage(() -> String.format("Table \"%s\" has status \"%s\". Blocking until deleted...",
                        d.tableName(), d.tableStatus()));
                // exponential backoff until table is active
                Thread.sleep((long)Math.pow(2, retry++) * EXP_BACKOFF_FACTOR);
                descr = DynamoDBUtil.describeTable(m_settings, conInfo, false);
            }
        }

        return new PortObject[] {inObjects[0]};
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
        final DynamoDBDeleteTableSettings s = new DynamoDBDeleteTableSettings();
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
