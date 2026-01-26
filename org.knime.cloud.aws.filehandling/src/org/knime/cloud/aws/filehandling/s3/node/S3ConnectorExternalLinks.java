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

/**
 * External links used in S3 Connector node descriptions and help text.
 * Make sure to escape the ampersand in URLs when used in HTML contexts.
 *
 * @author KNIME GmbH, Konstanz, Germany
 */
final class S3ConnectorExternalLinks {

    private S3ConnectorExternalLinks() {
        // utility class
    }

    /** AWS Java SDK documentation for ClientConfiguration socket timeout. */
    static final String AWS_SDK_SOCKET_TIMEOUT_DOC =
        "https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/"
            + "ClientConfiguration.html#setSocketTimeout-int-";

    /** AWS documentation for S3 Server-side encryption. */
    static final String AWS_S3_SERVER_SIDE_ENCRYPTION_DOC =
        "https://docs.aws.amazon.com/AmazonS3/latest/dev/serv-side-encryption.html";

    // Widget descriptions with embedded links (for use in @Widget annotations)

    static final String SOCKET_TIMEOUT_DESCRIPTION =
            "The socket read/write timeout. For further details see the <a href=\"" //
            + AWS_SDK_SOCKET_TIMEOUT_DOC //
            + "\">AWS documentation</a>.";

    static final String SSE_ENABLED_DESCRIPTION =
            "If selected, all data written to S3 will be encrypted with <a href=\"" //
            + AWS_S3_SERVER_SIDE_ENCRYPTION_DOC //
            + "\">Server-side encryption (SSE)</a>" //
            + " using SSE-S3, SSE-KMS or SSE-C."; //

}
