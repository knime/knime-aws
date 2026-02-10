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
 * Node Factory for the S3 Generic Connector node.
 *
 * @author Mareike Hoeger, KNIME GmbH, Konstanz, Germany
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
public class S3GenericConnectorNodeFactory extends ConfigurableNodeFactory<S3GenericConnectorNodeModel>
    implements NodeDialogFactory, KaiNodeInterfaceFactory {

    /**
     * File System Connection port name.
     */
    public static final String FILE_SYSTEM_CONNECTION_PORT_NAME = "File System Connection";

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var builder = new PortsConfigurationBuilder();
        builder.addOptionalInputPortGroup(FILE_SYSTEM_CONNECTION_PORT_NAME, FileSystemPortObject.TYPE);
        builder.addFixedOutputPortGroup("S3 File System Connection", FileSystemPortObject.TYPE);
        return Optional.of(builder);
    }

    @Override
    protected S3GenericConnectorNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new S3GenericConnectorNodeModel(creationConfig.getPortConfig().orElseThrow(IllegalStateException::new));
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<S3GenericConnectorNodeModel> createNodeView(
        final int viewIndex, final S3GenericConnectorNodeModel nodeModel) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    private static final String NODE_NAME = "Generic S3 Connector";

    private static final String NODE_ICON = "../../s3/node/file_system_connector.png";

    private static final String SHORT_DESCRIPTION = """
            Provides a file system connection to an S3-compatible endpoint.
            """;
    private static final String FULL_DESCRIPTION = """
            <p> This node connects to services that provide an S3-compatible API endpoint, for example <a
                href="https://min.io/">MinIO</a>. The resulting output port allows downstream nodes to access the data
                behind the endpoint as a file system, e.g. to read or write files and folders, or to perform other file
                system operations (browse/list files, copy, move, ...). If you want to connect to Amazon S3 on AWS,
                please use the Amazon S3 Connector node instead. </p> <p><b>Path syntax:</b> Paths for this file system
                are specified with a UNIX-like syntax, /mybucket/myfolder/myfile. An absolute consists of: <ol> <li>A
                leading slash ("/").</li> <li>Followed by the name of a bucket ("mybucket" in the above example),
                followed by a slash.</li> <li>Followed by the name of an object within the bucket ("myfolder/myfile" in
                the above example).</li> </ol> </p> <p><b>URI formats:</b> When you apply the <i>Path to URI</i> node to
                paths coming from this connector, you can create URIs with the following formats: <ol> <li> <b>Presigned
                https:// URLs</b> which contain credentials, that allow to access files for a certain amount of time
                (see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html">AWS
                documentation</a>). </li> <li> <b>s3:// URLs</b> to access the S3-compatible endpoint with the
                <tt>aws</tt> command line interface, or from inside Hadoop environments. </li> </ol> </p>
            """;

    private static final List<PortDescription> INPUT_PORTS = List.of(
            dynamicPort(FILE_SYSTEM_CONNECTION_PORT_NAME, FILE_SYSTEM_CONNECTION_PORT_NAME, """
                A file system connection to read the customer key, when <b>SSE-C</b> encryption mode is enabled.
                """)
    );

    private static final List<PortDescription> OUTPUT_PORTS = List.of(
            fixedPort("Generic S3 File System Connection", """
                Generic S3 File System Connection
                """)
    );

    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, S3GenericConnectorNodeParameters.class);
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
            S3GenericConnectorNodeParameters.class, //
            null, //
            NodeType.Source, //
            List.of(), //
            null //
        );
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, S3GenericConnectorNodeParameters.class));
    }

}
