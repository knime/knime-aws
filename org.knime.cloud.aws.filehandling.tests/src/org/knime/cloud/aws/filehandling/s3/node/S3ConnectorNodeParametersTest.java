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
 * ------------------------------------------------------------------------
 */
package org.knime.cloud.aws.filehandling.s3.node;

import java.io.FileInputStream;
import java.io.IOException;

import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.cloud.core.util.port.CloudConnectionInformationPortObjectSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.NodeParametersUtil;
import org.knime.testing.node.dialog.DefaultNodeSettingsSnapshotTest;
import org.knime.testing.node.dialog.SnapshotTestConfiguration;

/**
 * Snapshot tests for S3ConnectorNodeParameters.
 */
@SuppressWarnings("restriction")
final class S3ConnectorNodeParametersTest extends DefaultNodeSettingsSnapshotTest {

    protected S3ConnectorNodeParametersTest() {
        super(getConfig());
    }

    private static SnapshotTestConfiguration getConfig() {
        final var portsConfig = new S3ConnectorNodeFactory().createNodeCreationConfig().getPortConfig()
                .orElseThrow(() -> new IllegalStateException("Port config is not present"));

        return SnapshotTestConfiguration.builder() //
            .withPortsConfiguration(portsConfig) //
            .withInputPortObjectSpecs(createInputPortSpecs()) //
            .testJsonFormsForModel(S3ConnectorNodeParameters.class) //
            .testJsonFormsWithInstance(SettingsType.MODEL,
                () -> readSettings("S3ConnectorNodeParameters_sse_c_file.xml")) //
            .testNodeSettingsStructure(() -> readSettings("S3ConnectorNodeParameters_sse_c_file.xml")) //
            .testJsonFormsWithInstance(SettingsType.MODEL, () -> readSettings("S3ConnectorNodeParameters_sse_s3.xml"))
            .testNodeSettingsStructure(() -> readSettings("S3ConnectorNodeParameters_sse_s3.xml")) //
            .testJsonFormsWithInstance(SettingsType.MODEL, () -> readSettings("S3ConnectorNodeParameters_sse_kms.xml"))
            .testNodeSettingsStructure(() -> readSettings("S3ConnectorNodeParameters_sse_kms.xml")) //
            .testJsonFormsWithInstance(SettingsType.MODEL,
                () -> readSettings("S3ConnectorNodeParameters_sse_c_settings.xml")) //
            .testNodeSettingsStructure(() -> readSettings("S3ConnectorNodeParameters_sse_c_settings.xml")) //
            .build();
    }

    private static S3ConnectorNodeParameters readSettings(final String filename) {
        try {
            final var path =
                getSnapshotPath(S3ConnectorNodeParametersTest.class).getParent().resolve("node_settings")
                    .resolve(filename);
            try (var fis = new FileInputStream(path.toFile())) {
                var nodeSettings = NodeSettings.loadFromXML(fis);
                return NodeParametersUtil.loadSettings(
                    nodeSettings.getNodeSettings(SettingsType.MODEL.getConfigKey()),
                    S3ConnectorNodeParameters.class);
            }
        } catch (IOException | InvalidSettingsException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates the input port spec for the CloudConnectionInformation port.
     * This provides a mock AWS connection for testing purposes.
     *
     * @return array containing the CloudConnectionInformationPortObjectSpec
     */
    private static PortObjectSpec[] createInputPortSpecs() {
        var connInfo = new CloudConnectionInformation();
        connInfo.setHost("us-east-1"); // AWS region
        connInfo.setUser("test-access-key");
        connInfo.setPassword("test-secret-key");
        return new PortObjectSpec[]{new CloudConnectionInformationPortObjectSpec(connInfo)};
    }
}
