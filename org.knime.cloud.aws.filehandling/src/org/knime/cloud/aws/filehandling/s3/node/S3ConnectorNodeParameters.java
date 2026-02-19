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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.ArrayUtils;
import org.knime.cloud.aws.filehandling.s3.fs.S3FSConnection;
import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig;
import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig.SSEMode;
import org.knime.cloud.aws.filehandling.s3.node.AbstractS3ConnectorNodeParameters.KmsKeySettings.KmsKeyIdModeRef;
import org.knime.cloud.aws.filehandling.s3.node.S3ConnectorNodeParameters.S3ConnectorModification;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.cloud.core.util.port.CloudConnectionInformationPortObjectSpec;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FSConnectionProvider;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.WithCustomFileSystem;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification.WidgetGroupModifier;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.SuggestionsProvider;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.Message;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;

/**
 * Node parameters for Amazon S3 Connector.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
@Modification(S3ConnectorModification.class)
@SuppressWarnings("restriction")
final class S3ConnectorNodeParameters extends AbstractS3ConnectorNodeParameters {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(S3ConnectorNodeParameters.class);

    static final class S3ConnectorModification implements Modification.Modifier {

        @Override
        public void modify(final WidgetGroupModifier group) {
            group.find(InfoMessageRef.class).addAnnotation(TextMessage.class)
                .withProperty("value", S3InfoMessageProvider.class)
                .modify();
            group.find(KmsKeyIdModeRef.class).addAnnotation(SuggestionsProvider.class)
                .withProperty("value", KmsKeyChoicesProvider.class)
                .modify();
            group.find(WorkingDirectoryModRef.class).addAnnotation(WithCustomFileSystem.class)
                .withProperty("connectionProvider", WorkingDirectoryFileSystemProvider.class)
                .modify();
        }

    }

    /**
     * Intermediate state provider that extracts the CloudConnectionInformation from the input port.
     * Returns an error message if connection info is not available, otherwise returns the connection info.
     */
    static final class IntermediateConnectionStateProvider
        implements StateProvider<MessageAndData<CloudConnectionInformation>> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public MessageAndData<CloudConnectionInformation> computeState(final NodeParametersInput context) {
            PortObjectSpec[] specs = context.getInPortSpecs();

            if (ArrayUtils.isEmpty(specs) || !(specs[0] instanceof CloudConnectionInformationPortObjectSpec connSpec)) {
                return newNoConnectionInfoMessage(
                    "No input connected. Connect an Amazon Authenticator node to the input.");
            }

            CloudConnectionInformation connInfo = connSpec.getConnectionInformation();

            if (connInfo == null) {
                return newNoConnectionInfoMessage("No connection information found. "
                    + "Ensure the Amazon Authenticator node is executed successfully.");
            }
            return new MessageAndData<>(null, connInfo);
        }

        static MessageAndData<CloudConnectionInformation> newNoConnectionInfoMessage(final String msg) {
            return new MessageAndData<CloudConnectionInformation>(
                new Message("No connection to AWS", msg, MessageType.INFO), null);
        }

    }

    /**
     * Provides a file system connection for browsing S3 directories.
     * The connection is constructed from:
     * - CloudConnectionInformation from the shared IntermediateConnectionStateProvider
     * - Current user configuration (socket timeout, normalization, SSE settings)
     *
     * All relevant settings are captured via ValueReferences and used to create
     * a complete S3FSConnectionConfig for proper file browsing behavior.
     * Returns null when no connection is available (error is shown via S3InfoMessageProvider).
     */
    static final class WorkingDirectoryFileSystemProvider implements StateProvider<FSConnectionProvider> {

        // Shared connection state provider
        private Supplier<MessageAndData<CloudConnectionInformation>> m_connectionStateSupplier;

        // Member variables to hold supplier references for all configuration settings
        private Supplier<Integer> m_socketTimeoutSupplier;
        private Supplier<Boolean> m_normalizePathsSupplier;
        private Supplier<Boolean> m_sseEnabledSupplier;
        private Supplier<SSEMode> m_sseModeSupplier;
        private Supplier<KmsKeySettings> m_kmsKeySettingsSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_connectionStateSupplier = initializer.computeFromProvidedState(IntermediateConnectionStateProvider.class);
            // Use getValueSupplier() to register as dependencies (not triggers) - on demand only
            m_socketTimeoutSupplier = initializer.getValueSupplier(SocketTimeoutRef.class);
            m_normalizePathsSupplier = initializer.getValueSupplier(NormalizePathsRef.class);
            m_sseEnabledSupplier = initializer.getValueSupplier(SseEnabledRef.class);
            m_sseModeSupplier = initializer.getValueSupplier(SseModeRef.class);
            m_kmsKeySettingsSupplier = initializer.getValueSupplier(KmsKeySettingsRef.class);
        }

        @Override
        public FSConnectionProvider computeState(final NodeParametersInput context) {
            // Get connection info from shared provider
            var connState = m_connectionStateSupplier.get();
            final CloudConnectionInformation connInfo = connState.data();
            if (connInfo == null) {
                return () -> { throw new IOException("No connection to S3 could be established."); };
            }

            int socketTimeout = m_socketTimeoutSupplier.get();
            boolean normalizePaths = m_normalizePathsSupplier.get();
            boolean sseEnabled = m_sseEnabledSupplier.get();
            SSEMode sseMode = m_sseModeSupplier.get();
            KmsKeySettings kmsKeySettings = m_kmsKeySettingsSupplier.get();

            final S3FSConnectionConfig config = createS3ConnectionConfig(
                connInfo, socketTimeout, normalizePaths, sseEnabled, sseMode,
                kmsKeySettings);

            return () -> new S3FSConnection(config);
        }

        /**
         * Creates S3FSConnectionConfig from connection info and all user settings.
         * This mirrors the logic from S3ConnectorNodeSettings.toFSConnectionConfig().
         */
        private static S3FSConnectionConfig createS3ConnectionConfig( //
            final CloudConnectionInformation connInfo, //
            final int socketTimeout, //
            final boolean normalizePaths, //
            final boolean sseEnabled, //
            final SSEMode sseMode, //
            final KmsKeySettings kmsKeySettings) {

            // Create config with working directory "/" for browsing
            S3FSConnectionConfig config = new S3FSConnectionConfig("/", connInfo);

            // Apply user settings
            config.setNormalizePath(normalizePaths);
            config.setSocketTimeout(Duration.ofSeconds(socketTimeout));

            // Apply SSE settings if enabled
            if (sseEnabled) {
                config.setSseEnabled(true);
                config.setSseMode(sseMode);

                if (sseMode == SSEMode.KMS && kmsKeySettings != null) {
                    config.setSseKmsUseAwsManaged(kmsKeySettings.m_useAwsManagedKey);
                    if (!kmsKeySettings.m_useAwsManagedKey && kmsKeySettings.m_kmsKeyId != null) {
                        config.setSseKmsKeyId(kmsKeySettings.m_kmsKeyId);
                    }
                }
                // Note: SSE-C customer key handling is more complex (requires credentials provider)
                // and is not needed for file browsing, so we omit it here
            } else {
                config.setSseEnabled(false);
            }

            return config;
        }
    }

    /**
     * Message provider that aggregates error messages from intermediate state providers (connection, KMS keys).
     * Returns the first error message encountered, or empty if no errors.
     */
    static final class S3InfoMessageProvider implements StateProvider<Optional<TextMessage.Message>> {

        private Supplier<MessageAndData<CloudConnectionInformation>> m_connectionStateSupplier;
        private Supplier<MessageAndData<List<StringChoice>>> m_kmsKeysStateSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_connectionStateSupplier =
                    initializer.computeFromProvidedState(IntermediateConnectionStateProvider.class);
            m_kmsKeysStateSupplier = initializer.computeFromProvidedState(IntermediateKmsKeysStateProvider.class);
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput context) {
            // Check connection errors first (most fundamental)
            var connState = m_connectionStateSupplier.get();
            if (connState.message() != null) {
                return Optional.of(connState.message());
            }

            var kmsState = m_kmsKeysStateSupplier.get();
            if (kmsState.message() != null) {
                return Optional.of(kmsState.message());
            }

            return Optional.empty();
        }
    }

    /**
     * Intermediate state provider for KMS keys that returns both error message and data.
     * This allows the error to be displayed in the UI while still providing choices.
     * Uses the shared IntermediateConnectionStateProvider for connection info.
     */
    static final class IntermediateKmsKeysStateProvider //
        implements StateProvider<MessageAndData<List<StringChoice>>> {

        private Supplier<MessageAndData<CloudConnectionInformation>> m_connectionStateSupplier;
        private Supplier<Boolean> m_sseEnabledSupplier;
        private Supplier<SSEMode> m_sseModeSupplier;
        private Supplier<KmsKeySettings> m_kmsKeySettingsSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_connectionStateSupplier =
                    initializer.computeFromProvidedState(IntermediateConnectionStateProvider.class);
            m_sseEnabledSupplier = initializer.computeFromValueSupplier(SseEnabledRef.class);
            m_sseModeSupplier = initializer.computeFromValueSupplier(SseModeRef.class);
            m_kmsKeySettingsSupplier = initializer.computeFromValueSupplier(KmsKeySettingsRef.class);
        }

        @Override
        public MessageAndData<List<StringChoice>> computeState(final NodeParametersInput context) {
            return S3ConnectorNodeParameterUtil.getIntermediateKmsKeys(
                m_connectionStateSupplier, m_sseEnabledSupplier, m_sseModeSupplier, m_kmsKeySettingsSupplier, LOGGER);
        }

    }

    /**
     * Provider that returns the list of available KMS keys from the intermediate state provider.
     * Requires permissions: kms:ListKeys, kms:DescribeKey and optionally kms:ListAliases.
     */
    static final class KmsKeyChoicesProvider implements StringChoicesProvider {

        private Supplier<MessageAndData<List<StringChoice>>> m_kmsKeysStateSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_kmsKeysStateSupplier = initializer.computeFromProvidedState(IntermediateKmsKeysStateProvider.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            // Get data from intermediate state (errors are reported via TextMessage)
            return m_kmsKeysStateSupplier.get().data();
        }
    }

}
