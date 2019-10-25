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
 *   20.08.2019 (Mareike Hoeger, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.cloud.aws.filehandling.nodes.S3connection;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.knime.cloud.aws.filehandling.connections.S3Connection;
import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication.AuthenticationType;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.workflow.FSConnectionFlowVariableValue;
import org.knime.core.node.workflow.FSConnectionNode;
import org.knime.core.util.Pair;
import org.knime.filehandling.core.connections.FSConnectionRegistry;

import com.amazonaws.services.s3.AmazonS3;

/**
 *
 * @author Mareike Hoeger, KNIME GmbH, Konstanz, Germany
 */
public class S3ConnectionNodeModel extends NodeModel implements FSConnectionNode {

    S3ConnectionConfig m_config = new S3ConnectionConfig(AmazonS3.ENDPOINT_PREFIX);

    private S3Connection m_fsconn;

    private String m_connectionKey;

    /**
     * The NodeModel for the S3Connection node
     */
    public S3ConnectionNodeModel() {
        super(new PortType[]{AmazonConnectionInformationPortObject.TYPE}, new PortType[]{FlowVariablePortObject.TYPE});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final AmazonConnectionInformationPortObject awsConnectionInfo =
            (AmazonConnectionInformationPortObject)inObjects[0];

        m_fsconn = new S3Connection(awsConnectionInfo.getConnectionInformation());

        m_connectionKey = FSConnectionRegistry.getInstance().register(m_fsconn);
        final String connectionName = m_config.getConnectionName();
        pushFSConnectionFlowVariable(connectionName, new FSConnectionFlowVariableValue(m_connectionKey));

        return new PortObject[]{FlowVariablePortObject.INSTANCE};
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) {
        return new PortObjectSpec[]{null};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // no internals

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // no internals
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_config.saveSettingsTo(settings);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.loadValidatedSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        try {
            if (m_fsconn != null) {
                m_fsconn.closeFileSystem();
            }
        } catch (final IOException ex) {

        }

        FSConnectionRegistry.getInstance().deregister(m_connectionKey);

    }

    static HashMap<AuthenticationType, Pair<String, String>> getNameMap() {
        final HashMap<AuthenticationType, Pair<String, String>> nameMap = new HashMap<>();

        nameMap.put(AuthenticationType.USER_PWD,
            new Pair<>("Access Key ID and Secret Key", "Access Key ID and Secret Access Key based authentication"));
        nameMap.put(AuthenticationType.KERBEROS, new Pair<>("Default Credential Provider Chain",
            "Use the Default Credential Provider Chain for authentication"));
        nameMap.put(AuthenticationType.NONE, new Pair<>("Anonymous Credentials", "Use Anonymous Credentials"));
        return nameMap;
    }

}
