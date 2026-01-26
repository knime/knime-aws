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
package org.knime.cloud.aws.filehandling.s3.node;

import static org.knime.node.impl.description.PortDescription.dynamicPort;
import static org.knime.node.impl.description.PortDescription.fixedPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultKaiNodeInterface;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterface;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterfaceFactory;
import org.knime.filehandling.core.port.FileSystemPortObject;
import org.knime.node.impl.description.DefaultNodeDescriptionUtil;
import org.knime.node.impl.description.PortDescription;

/**
 *
 * @author Mareike Hoeger, KNIME GmbH, Konstanz, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
public class S3ConnectorNodeFactory extends ConfigurableNodeFactory<S3ConnectorNodeModel> implements NodeDialogFactory, KaiNodeInterfaceFactory {

    /**
     * File System Connection port name.
     */
    public static final String FILE_SYSTEM_CONNECTION_PORT_NAME = "File System Connection";

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final PortsConfigurationBuilder builder = new PortsConfigurationBuilder();
        builder.addFixedInputPortGroup("Connection information port", AmazonConnectionInformationPortObject.TYPE);
        builder.addOptionalInputPortGroup(FILE_SYSTEM_CONNECTION_PORT_NAME, FileSystemPortObject.TYPE);
        builder.addFixedOutputPortGroup("S3 File System Connection", FileSystemPortObject.TYPE);
        return Optional.of(builder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected S3ConnectorNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new S3ConnectorNodeModel(creationConfig.getPortConfig().orElseThrow(IllegalStateException::new));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<S3ConnectorNodeModel> createNodeView(final int viewIndex, final S3ConnectorNodeModel nodeModel) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    private static final String NODE_NAME = "Amazon S3 Connector";
    private static final String NODE_ICON = "../../s3/node/file_system_connector.png";
    private static final String SHORT_DESCRIPTION = """
            Provides a file system connection to Amazon S3.
            """;
    private static final String FULL_DESCRIPTION = """
            <p> This node configures the connection information that will be used to connect to Amazon S3. Using
                this connection the other KNIME remote file handling nodes such as Excel Reader and Excel Writer can
                download and upload files from and to Amazon S3. </p> <p> For further documentation please take a look
                at the <a href="http://docs.aws.amazon.com/AmazonS3/latest/gsg/GetStartedWithS3.html">AWS
                Documentation</a>. </p> <p><b>Path syntax:</b> Paths for Amazon S3 are specified with a UNIX-like
                syntax, /mybucket/myfolder/myfile. An absolute for S3 consists of: <ol> <li>A leading slash ("/").</li>
                <li>Followed by the name of a bucket ("mybucket" in the above example), followed by a slash.</li>
                <li>Followed by the name of an object within the bucket ("myfolder/myfile" in the above example).</li>
                </ol> </p> <p><b>URI formats:</b> When you apply the <i>Path to URI</i> node to paths coming from this
                connector, you can create URIs with the following formats: <ol> <li><b>Presigned https:// URLs</b> which
                contain credentials, that allow to access files for a certain amount of time (see <a
                href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html">AWS
                documentation</a>).</li> <li><b>s3:// URLs</b> to access Amazon S3 with the <tt>aws</tt> command line
                interface, or inside Hadoop environments.</li> </ol> </p>
            """;
    private static final List<PortDescription> INPUT_PORTS = List.of(
            dynamicPort("File System Connection", "File System Connection", """
                A file system connection to read/write the customer key, when <b>SSE-C</b> encryption mode is enabled.
                """),
            fixedPort("Connection information port", """
                Port object containing the AWS connection information.
                """)
    );
    private static final List<PortDescription> OUTPUT_PORTS = List.of(
            fixedPort("S3 File System Connection", """
                S3 File System Connection.
                """)
    );

    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, S3ConnectorNodeParameters.class);
    }

    @Override
    public NodeDescription createNodeDescription() {
        return DefaultNodeDescriptionUtil.createNodeDescription( //
            NODE_NAME, //
            NODE_ICON, //
            INPUT_PORTS, //
            OUTPUT_PORTS, //
            SHORT_DESCRIPTION, //
            FULL_DESCRIPTION, //
            List.of(), //
            S3ConnectorNodeParameters.class, //
            null, //
            NodeType.Source, //
            List.of(), //
            null //
        );
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, S3ConnectorNodeParameters.class));
    }

}
