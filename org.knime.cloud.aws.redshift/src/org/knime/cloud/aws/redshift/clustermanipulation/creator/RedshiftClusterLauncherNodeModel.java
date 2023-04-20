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
 *   Jul 31, 2016 (budiyanto): created
 */
package org.knime.cloud.aws.redshift.clustermanipulation.creator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.knime.cloud.aws.redshift.clustermanipulation.util.RedshiftClusterUtility;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication;
import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication.AuthenticationType;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.port.flowvariable.FlowVariablePortObjectSpec;
import org.knime.core.util.Pair;

import software.amazon.awssdk.services.redshift.RedshiftClient;
import software.amazon.awssdk.services.redshift.model.Cluster;
import software.amazon.awssdk.services.redshift.model.ClusterAlreadyExistsException;
import software.amazon.awssdk.services.redshift.model.CreateClusterRequest;
import software.amazon.awssdk.services.redshift.model.DescribeClustersRequest;
import software.amazon.awssdk.services.redshift.model.UnauthorizedOperationException;

/**
 * Create an amazon Redshift cluster given all it's specifications
 *
 * @author Ole Ostergaard, KNIME.com
 */
class RedshiftClusterLauncherNodeModel extends NodeModel {

    /** The endpoints authentification credentials */
    protected final RedshiftClusterLauncherNodeSettings m_settings = createNodeSettings();

    static RedshiftClusterLauncherNodeSettings createRedshiftConnectionModel() {
        return new RedshiftClusterLauncherNodeSettings(RedshiftClient.SERVICE_METADATA_ID);
    }

    static List<String> getClusterTypes() {
        return List.of("ds2.xlarge", "ds2.8xlarge", "dc1.large", "dc1.8xlarge",
            "dc2.large", "dc2.8xlarge", "ra3.xlplus", "ra3.4xlarge", "ra3.16xlarge");
    }

    /** Keyword for single-node operation */
    protected static String SINGLE_NODE = "single-node";

    /** Keyword for multi-node operation */
    protected static String MULTI_NODE = "multi-node";

    /**
     * @return the {@link SettingsModelAuthentication} for RedshiftClusterLauncherNodeModel
     */
    protected static RedshiftClusterLauncherNodeSettings createNodeSettings() {
        return new RedshiftClusterLauncherNodeSettings(RedshiftClient.SERVICE_METADATA_ID);
    }

    static HashMap<AuthenticationType, Pair<String, String>> getNameMap() {
        final HashMap<AuthenticationType, Pair<String, String>> nameMap = new HashMap<>();
        nameMap.put(AuthenticationType.USER_PWD, new Pair<String, String>("Access Key ID and Secret Key",
            "Access Key ID and Secret Access Key based authentication"));
        nameMap.put(AuthenticationType.KERBEROS, new Pair<String, String>("Default Credential Provider Chain",
            "Use the Default Credential Provider Chain for authentication"));
        return nameMap;
    }

    /**
     * Constructor for the node model.
     */
    protected RedshiftClusterLauncherNodeModel() {
        super(new PortType[]{}, new PortType[]{FlowVariablePortObject.TYPE});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        try (final var client = RedshiftClusterUtility.getClient(m_settings, getCredentialsProvider())) {
            final var request = createClusterRequest();

            try {
                exec.setMessage("Cluster creation requested");
                var cluster = client.createCluster(request).cluster();
                String status = cluster.clusterStatus();
                exec.setMessage("Waiting for cluster availability");
                while (!status.equalsIgnoreCase("available")) {
                    exec.checkCanceled();
                    Thread.sleep(m_settings.getPollingInterval());
                    cluster = getCluster(client, m_settings.getClusterName());
                    status = cluster.clusterStatus();
                    exec.setMessage("Cluster status: " + status);
                }
                final var endpoint = cluster.endpoint();
                pushFlowvariables(endpoint.address(), endpoint.port(), cluster.dbName(), cluster.clusterIdentifier());
            } catch (ClusterAlreadyExistsException e) {
                if (m_settings.failIfExists()) {
                    final var cluster = getCluster(client, m_settings.getClusterName());
                    if (cluster.clusterStatus().equals("deleting")) {
                        throw new InvalidSettingsException("Cluster " + m_settings.getClusterName() + " is being deleted",
                            e);
                    } else {
                        throw new InvalidSettingsException("Cluster " + m_settings.getClusterName() + " alread exists.", e);
                    }
                } else {
                    final var cluster = getCluster(client, m_settings.getClusterName());
                    if (cluster.clusterStatus().equals("deleting")) {
                        throw new InvalidSettingsException("Cluster " + m_settings.getClusterName() + " is being deleted",
                            e);
                    }
                    final var endpoint = cluster.endpoint();
                    pushFlowvariables(endpoint.address(), endpoint.port(), cluster.dbName(), cluster.clusterIdentifier());
                }
            } catch (UnauthorizedOperationException e) {
                throw new InvalidSettingsException("Check user permissons.", e);
            } catch (Exception e) {
                throw e;
            }
        }
        return new PortObject[]{FlowVariablePortObject.INSTANCE};
    }

    /**
     * Creates the request for creating a cluster according to the settings.
     *
     * @return The request for creating the cluster
     */
    private CreateClusterRequest createClusterRequest() {

        String masterUser;
        String masterPW;
        if (m_settings.getClusterCredentials().getAuthenticationType() == AuthenticationType.CREDENTIALS) {
            final var credentialsProvider = getCredentialsProvider();
            final var iCredentials = credentialsProvider.get(m_settings.getClusterCredentials().getCredential());
            masterUser = iCredentials.getLogin();
            masterPW = iCredentials.getPassword();
        } else {
            masterUser = m_settings.getMasterName();
            masterPW = m_settings.getMasterPassword();
        }

        final String clusterName = m_settings.getClusterName();
        final String clusterType = (m_settings.getNodeNumber() > 1) ? MULTI_NODE : SINGLE_NODE;

        final var requestBuilder = CreateClusterRequest.builder().clusterIdentifier(clusterName)
            .masterUsername(masterUser).masterUserPassword(masterPW).nodeType(m_settings.getNodeType())
            .clusterType(clusterType).dbName(m_settings.getDefaultDBName()).port(m_settings.getPort());

        if (clusterType.equals(MULTI_NODE)) {
            requestBuilder.numberOfNodes(m_settings.getNodeNumber());
        }
        return requestBuilder.build();
    }

    private static Cluster getCluster(final RedshiftClient client, final String clusterName) {
        final var clusterRequest = DescribeClustersRequest.builder()
                .clusterIdentifier(clusterName).build();
        final var response = client.describeClusters(clusterRequest);
        return response.clusters().get(0);
    }

    /**
     * Pushes the given information to the flowvariables.
     *
     * @param hostname The hostname to be push to the flowvariable
     * @param port The port to be push to the flowvariable
     * @param defaultDB The default database name to be push to the flowvariable
     * @param clusterName the cluster name to be pushed to the flowvariable
     */
    protected void pushFlowvariables(final String hostname, final Integer port, final String defaultDB,
        final String clusterName) {
        final Set<String> variables = getAvailableFlowVariables().keySet();
        String name = "redshift" + "Hostname";
        String postfix = "";
        if (variables.contains(name)) {
            int i = 2;
            postfix += "_";
            while (variables.contains(name + i)) {
                i++;
            }
            postfix += i;
        }
        pushFlowVariableString(RedshiftClient.SERVICE_METADATA_ID + "Hostname" + postfix, hostname);
        pushFlowVariableInt(RedshiftClient.SERVICE_METADATA_ID + "Port" + postfix, port);
        pushFlowVariableString(RedshiftClient.SERVICE_METADATA_ID + "DatabaseName" + postfix, defaultDB);
        pushFlowVariableString(RedshiftClient.SERVICE_METADATA_ID + "ClusterName" + postfix, clusterName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[]{FlowVariablePortObjectSpec.INSTANCE};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.loadValidatedSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // nothing to do
    }

}
