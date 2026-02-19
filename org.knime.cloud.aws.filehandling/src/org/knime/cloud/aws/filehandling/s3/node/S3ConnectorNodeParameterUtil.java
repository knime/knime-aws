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
 *   2026-02-20 (magnus): created
 */
package org.knime.cloud.aws.filehandling.s3.node;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.knime.cloud.aws.filehandling.s3.AwsUtils;
import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig.SSEMode;
import org.knime.cloud.aws.filehandling.s3.node.AbstractS3ConnectorNodeParameters.KmsKeySettings;
import org.knime.cloud.aws.filehandling.s3.node.AbstractS3ConnectorNodeParameters.MessageAndData;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.node.NodeLogger;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.KeyListEntry;

/**
 * Utility class for node parameter implementations of the S3 nodes.
 *
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 */
final class S3ConnectorNodeParameterUtil {

    private S3ConnectorNodeParameterUtil() {
        // utility class
    }

    /**
     * Retrieves the list of KMS keys from AWS if all conditions are met, otherwise returns an empty list:
     *
     * @param connectionStateSupplier Supplier for the current connection state, providing
     * {@link CloudConnectionInformation} or error message
     * @param sseEnabledSupplier Supplier for whether SSE is enabled
     * @param sseModeSupplier Supplier for the selected SSE mode
     * @param kmsKeySettingsSupplier Supplier for the current KMS key settings, providing {@link KmsKeySettings} or null
     * @param logger {@link NodeLogger} for logging any errors during key retrieval
     * @return {@link MessageAndData} containing either an error message or the list of KMS keys as
     * {@link StringChoice} objects
     */
    public static MessageAndData<List<StringChoice>> getIntermediateKmsKeys(
        final Supplier<MessageAndData<CloudConnectionInformation>> connectionStateSupplier,
        final Supplier<Boolean> sseEnabledSupplier,
        final Supplier<SSEMode> sseModeSupplier,
        final Supplier<KmsKeySettings> kmsKeySettingsSupplier,
        final NodeLogger logger) {
        // Check all 3 conditions before making network call
        if (!shouldFetchKeys(sseEnabledSupplier, sseModeSupplier, kmsKeySettingsSupplier)) {
            return new MessageAndData<>(null, Collections.emptyList());
        }

        // Get connection info from shared provider
        var connState = connectionStateSupplier.get();
        if (connState.data() == null) {
            // Connection error already reported by IntermediateConnectionStateProvider
            // Return empty list without additional error message to avoid duplicates
            return new MessageAndData<>(null, Collections.emptyList());
        }

        try {
            List<StringChoice> keys = fetchKmsKeys(connState.data(), logger);
            return new MessageAndData<>(null, keys);
        } catch (AwsServiceException e) {
            // AWS service error - show user-friendly error with details
            logger.debug("AWS service error fetching KMS keys: " + e.getMessage(), e);
            return new MessageAndData<>( //
                new TextMessage.Message("Error retrieving KMS keys from AWS", //
                    "Failed to retrieve KMS keys. Verify your IAM permissions (kms:ListKeys, kms:DescribeKey, " //
                        + "kms:ListAliases).\nDetails: " //
                        + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), //
                    MessageType.ERROR), //
                Collections.emptyList());
        } catch (Exception e) { // NOSONAR
            // Other errors
            logger.debug("Error fetching KMS keys: " + e.getMessage(), e);
            return new MessageAndData<>( //
                new TextMessage.Message("Error retrieving KMS keys", //
                    "An unexpected error occurred.\nDetails: " //
                        + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), //
                    MessageType.ERROR), //
                Collections.emptyList());
        }
    }

    /**
     * Checks if all conditions are met to fetch KMS keys:
     * 1. SSE is enabled
     * 2. SSE mode is KMS
     * 3. "Use default AWS managed key" is not checked
     */
    private static boolean shouldFetchKeys(final Supplier<Boolean> sseEnabledSupplier,
        final Supplier<SSEMode> sseModeSupplier,
        final Supplier<KmsKeySettings> kmsKeySettingsSupplier) {
        boolean sseEnabled = Boolean.TRUE.equals(sseEnabledSupplier.get());
        if (!sseEnabled) {
            return false;
        }

        SSEMode sseMode = sseModeSupplier.get();
        if (sseMode != SSEMode.KMS) {
            return false;
        }

        KmsKeySettings kmsSettings = kmsKeySettingsSupplier.get();
        return kmsSettings == null || kmsSettings.m_useAwsManagedKey ? false : true;
    }

    private static List<StringChoice> fetchKmsKeys(final CloudConnectionInformation connInfo, final NodeLogger logger) {
        try (KmsClient client = KmsClient.builder() //
            .region(Region.of(connInfo.getHost())) //
            .credentialsProvider(AwsUtils.getCredentialProvider(connInfo)) //
            .build()) {

            List<KeyListEntry> keys = client.listKeys().keys();
            Map<String, String> aliases = fetchAliases(client, logger);

            return keys.stream().map(key -> {
                String keyId = key.keyId();
                String alias = aliases.get(keyId);
                // Text shows "alias (keyId)" if alias exists, otherwise just keyId
                String text = (alias != null && !alias.isEmpty()) //
                    ? String.format("%s (%s)", alias, keyId) //
                    : keyId;
                return new StringChoice(keyId, text);
            }).toList();
        }
    }

    private static Map<String, String> fetchAliases(final KmsClient client, final NodeLogger logger) {
        try {
            return client.listAliases().aliases().stream() //
                .filter(alias -> alias.targetKeyId() != null) //
                .collect(Collectors.toMap(AliasListEntry::targetKeyId, AliasListEntry::aliasName));
        } catch (AwsServiceException ex) {
            // Aliases are optional - don't fail, just return empty map
            logger.debug("Could not fetch KMS key aliases: " + ex.getMessage(), ex);
            return Collections.emptyMap();
        }
    }

}
