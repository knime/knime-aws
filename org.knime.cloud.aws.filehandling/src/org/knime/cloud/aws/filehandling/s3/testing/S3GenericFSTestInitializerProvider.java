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
 */
package org.knime.cloud.aws.filehandling.s3.testing;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.knime.cloud.aws.filehandling.s3.MultiRegionS3Client;
import org.knime.cloud.aws.filehandling.s3.fs.S3FSConnection;
import org.knime.cloud.aws.filehandling.s3.fs.S3FileSystem;
import org.knime.cloud.aws.filehandling.s3.fs.S3GenericFSDescriptorProvider;
import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig;
import org.knime.core.node.util.CheckUtils;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.meta.FSType;

/**
 * Initializer provider for generic S3 tests. Reads all s3 relevant properties from the configuration
 * and establishes a connection to custom S3 compatible endpoint.
 *
 * @author Sascha Wolke, KNIME GmbH
 */
public class S3GenericFSTestInitializerProvider extends AbstractS3FSTestInitializerProvider {

    @SuppressWarnings("resource")
    @Override
    public S3FSTestInitializer setup(final Map<String, String> config) throws IOException {

        validateConfiguration(config);
        CheckUtils.checkArgumentNotNull(config.get("endpoint"), "endpoint must not be null");

        final S3FSConnectionConfig s3config = createConnConfig(config);
        s3config.setOverrideEndpoint(true);
        s3config.setEndpointUrl(URI.create(config.get("endpoint")));
        s3config.setPathStyle(true);

        final var connection = new S3FSConnection(s3config);

        // we tests again an empty MinIO docker container, ensure the bucket exists
        final MultiRegionS3Client client = ((S3FileSystem)connection.getFileSystem()).getClient();
        final String bucket = ((S3FileSystem)connection.getFileSystem()).getPath(config.get("workingDirPrefix")).getBucketName();
        if (client.getBucket(bucket) == null) {
            client.createBucket(bucket);
        }

        return new S3FSTestInitializer(connection);
    }

    @Override
    public FSType getFSType() {
        return S3GenericFSDescriptorProvider.FS_TYPE;
    }

    @Override
    public FSLocationSpec createFSLocationSpec(final Map<String, String> config) {
        return S3FSConnectionConfig.createGenericS3FSLocationSpec(URI.create(config.get("endpoint")));
    }
}
