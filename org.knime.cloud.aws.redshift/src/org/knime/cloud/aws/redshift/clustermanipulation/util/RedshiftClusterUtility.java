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
 *   Apr 4, 2017 (oole): created
 */
package org.knime.cloud.aws.redshift.clustermanipulation.util;

import org.knime.core.node.defaultnodesettings.SettingsModelAuthentication.AuthenticationType;
import org.knime.core.node.workflow.CredentialsProvider;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.redshift.RedshiftClient;

/**
 * Utility class for the Amazon Redshift cluster manipulation nodes
 *
 * @author Ole Ostergaard, KNIME.com
 */
public final class RedshiftClusterUtility {

    /**
     * Returns an {@link RedshiftClient} according to the given settings and credential provider.
     *
     * @param settings The settings to use for the client
     * @param credentialsProvider The credential provider to use for the client
     * @return An {@link RedshiftClient} according to the given settings and credential provider
     */
    @SuppressWarnings("resource")
    public static RedshiftClient getClient(final RedshiftGeneralSettings settings,
        final CredentialsProvider credentialsProvider) {

        final AwsCredentialsProvider awsCredentialProvider;

        if (settings.getAuthenticationModel().getAuthenticationType() == AuthenticationType.KERBEROS) {
            awsCredentialProvider = DefaultCredentialsProvider.builder().build();
        } else if (settings.getAuthenticationModel().getAuthenticationType() == AuthenticationType.CREDENTIALS) {
            final var iCredentials = credentialsProvider.get(settings.getAuthenticationModel().getCredential());
            awsCredentialProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(iCredentials.getLogin(), iCredentials.getPassword()));
        } else {
            awsCredentialProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(settings.getUserValue(), settings.getPasswordValue()));
        }
        return RedshiftClient.builder().region(Region.of(settings.getRegion()))
                .credentialsProvider(awsCredentialProvider).build();
    }

    private RedshiftClusterUtility () {
    }
}
