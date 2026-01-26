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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.knime.cloud.aws.filehandling.s3.AwsUtils;
import org.knime.cloud.aws.filehandling.s3.fs.S3FSConnection;
import org.knime.cloud.aws.filehandling.s3.fs.S3FileSystem;
import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig;
import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig.SSEMode;
import org.knime.cloud.aws.filehandling.s3.node.S3ConnectorNodeSettings.CustomerKeySource;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.cloud.core.util.port.CloudConnectionInformationPortObjectSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.CredentialsProvider;
import org.knime.core.webui.node.dialog.defaultdialog.NodeParametersInputImpl;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FSConnectionProvider;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileReaderWidget;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelection;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelectionWidget;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.LegacyReaderFileSelectionPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.SingleFileSelectionMode;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.WithCustomFileSystem;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.WidgetGroup;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistable;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.RadioButtonsWidget;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.SuggestionsProvider;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.Message;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.KeyListEntry;

/**
 * Node parameters for Amazon S3 Connector.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings("restriction")
final class S3ConnectorNodeParameters implements NodeParameters {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(S3ConnectorNodeParameters.class);

    @Section(title = "File system settings")
    interface FileSystemSettings {
    }

    @Section(title = "Connection settings")
    @After(FileSystemSettings.class)
    @Advanced
    interface ConnectionSettings {
    }

    @Section(title = "Server-side encryption (SSE)")
    @After(ConnectionSettings.class)
    @Advanced
    interface ServerSideEncryption {
    }

    // ====== Error Message Display ======

    /** Return type of a state provider that provides a message and some data. */
    private record MessageAndData<T>(TextMessage.Message message, T data) {
    }

    /**
     * TextMessage field displayed at the top of the dialog to show errors from intermediate state providers.
     * Only visible when there's an error message to show.
     */
    @TextMessage(S3InfoMessageProvider.class)
    Void m_infoMessage;

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
     * Message provider that aggregates error messages from intermediate state providers (connection, KMS keys).
     * Returns the first error message encountered, or empty if no errors.
     */
    static final class S3InfoMessageProvider implements StateProvider<Optional<TextMessage.Message>> {

        private Supplier<MessageAndData<CloudConnectionInformation>> m_connectionStateSupplier;
        private Supplier<MessageAndData<List<StringChoice>>> m_kmsKeysStateSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_connectionStateSupplier = initializer.computeFromProvidedState(IntermediateConnectionStateProvider.class);
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
            m_connectionStateSupplier = initializer.computeFromProvidedState(IntermediateConnectionStateProvider.class);
            m_sseEnabledSupplier = initializer.computeFromValueSupplier(SseEnabledRef.class);
            m_sseModeSupplier = initializer.computeFromValueSupplier(SseModeRef.class);
            m_kmsKeySettingsSupplier = initializer.computeFromValueSupplier(KmsKeySettingsRef.class);
        }

        @Override
        public MessageAndData<List<StringChoice>> computeState(final NodeParametersInput context) {
            // Check all 3 conditions before making network call
            if (!shouldFetchKeys()) {
                return new MessageAndData<>(null, Collections.emptyList());
            }

            // Get connection info from shared provider
            var connState = m_connectionStateSupplier.get();
            if (connState.data() == null) {
                // Connection error already reported by IntermediateConnectionStateProvider
                // Return empty list without additional error message to avoid duplicates
                return new MessageAndData<>(null, Collections.emptyList());
            }

            try {
                List<StringChoice> keys = fetchKmsKeys(connState.data());
                return new MessageAndData<>(null, keys);
            } catch (AwsServiceException e) {
                // AWS service error - show user-friendly error with details
                LOGGER.debug("AWS service error fetching KMS keys: " + e.getMessage(), e);
                return new MessageAndData<>( //
                    new TextMessage.Message("Error retrieving KMS keys from AWS", //
                        "Failed to retrieve KMS keys. Verify your IAM permissions (kms:ListKeys, kms:DescribeKey, " //
                            + "kms:ListAliases).\nDetails: " //
                            + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), //
                        MessageType.ERROR), //
                    Collections.emptyList());
            } catch (Exception e) {
                // Other errors
                LOGGER.debug("Error fetching KMS keys: " + e.getMessage(), e);
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
        private boolean shouldFetchKeys() {
            boolean sseEnabled = Boolean.TRUE.equals(m_sseEnabledSupplier.get());
            if (!sseEnabled) {
                return false;
            }

            SSEMode sseMode = m_sseModeSupplier.get();
            if (sseMode != SSEMode.KMS) {
                return false;
            }

            KmsKeySettings kmsSettings = m_kmsKeySettingsSupplier.get();
            if (kmsSettings == null || kmsSettings.m_useAwsManagedKey) {
                return false;
            }

            return true;
        }

        private static List<StringChoice> fetchKmsKeys(final CloudConnectionInformation connInfo) {
            try (KmsClient client = KmsClient.builder() //
                .region(Region.of(connInfo.getHost())) //
                .credentialsProvider(AwsUtils.getCredentialProvider(connInfo)) //
                .build()) {

                List<KeyListEntry> keys = client.listKeys().keys();
                Map<String, String> aliases = fetchAliases(client);

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

        private static Map<String, String> fetchAliases(final KmsClient client) {
            try {
                return client.listAliases().aliases().stream() //
                    .filter(alias -> alias.targetKeyId() != null) //
                    .collect(Collectors.toMap(AliasListEntry::targetKeyId, AliasListEntry::aliasName));
            } catch (AwsServiceException ex) {
                // Aliases are optional - don't fail, just return empty map
                LOGGER.debug("Could not fetch KMS key aliases: " + ex.getMessage(), ex);
                return Collections.emptyMap();
            }
        }
    }

    // ====== File System Settings ======

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

    @Widget(title = "Working directory", description = """
            Specifies the <i>working directory</i> using the path syntax explained above. The working directory must be
            specified as an absolute path. A working directory allows downstream nodes to access files/folders using
            <i>relative</i> paths, i.e. paths that do not have a leading slash. If not specified, the default working
            directory is "/".
            """)
    @Persist(configKey = S3ConnectorNodeSettings.KEY_WORKING_DIRECTORY)
    @Layout(FileSystemSettings.class)
    @FileSelectionWidget(value = SingleFileSelectionMode.FOLDER, placeholder = S3FileSystem.PATH_SEPARATOR)
    @WithCustomFileSystem(connectionProvider = WorkingDirectoryFileSystemProvider.class)
    String m_workingDirectory = S3FileSystem.PATH_SEPARATOR;

    static final class NormalizePathsRef implements BooleanReference {
    }

    @Widget(title = "Normalize paths",
        description = """
                Determines if the path normalization should be applied. Path normalization eliminates redundant
                components of a path like, e.g. <tt>'/a/../b/./c'</tt> can be normalized to <tt>'/b/c'</tt>. When these
                redundant components like <tt>'../'</tt> or <tt>'.'</tt> are part of an existing object, then
                normalization must be deactivated in order to access them properly.
                """)
    @Persist(configKey = S3ConnectorNodeSettings.KEY_NORMALIZE_PATHS)
    @ValueReference(NormalizePathsRef.class)
    @Layout(FileSystemSettings.class)
    boolean m_normalizePaths = true;

    // ====== Connection Settings ======

    static final class SocketTimeoutRef implements ParameterReference<Integer> {
    }

    @Widget(title = "Read/write timeout in seconds", description = S3ConnectorExternalLinks.SOCKET_TIMEOUT_DESCRIPTION,
        advanced = true)
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
    @Persist(configKey = S3ConnectorNodeSettings.KEY_SOCKET_TIMEOUTS)
    @ValueReference(SocketTimeoutRef.class)
    @Layout(ConnectionSettings.class)
    int m_socketTimeout = S3FSConnectionConfig.DEFAULT_SOCKET_TIMEOUT_SECONDS;

    // ====== Server-Side Encryption ======

    static final class SseEnabledRef implements BooleanReference {
    }

    @Widget(title = "Enable server-side encryption (SSE)",
        description = S3ConnectorExternalLinks.SSE_ENABLED_DESCRIPTION, advanced = true)
    @Persist(configKey = S3ConnectorNodeSettings.KEY_SSE_ENABLED)
    @ValueReference(SseEnabledRef.class)
    @Layout(ServerSideEncryption.class)
    boolean m_sseEnabled;

    @Widget(title = "Encryption method",
        description = "Select the server-side encryption method to use.", advanced = true)
    @Persistor(SseModePersistor.class)
    @Effect(predicate = SseEnabledRef.class, type = EffectType.SHOW)
    @ValueReference(SseModeRef.class)
    @Layout(ServerSideEncryption.class)
    SSEMode m_sseMode = SSEMode.S3;

    static final class SseModeRef implements ParameterReference<SSEMode> {
    }

    /**
     * Custom persistor for SSEMode enum.
     *
     * Cannot use EnumFieldPersistor because SSEMode uses a custom key field (e.g., "SSE-S3")
     * for serialization instead of the enum constant name (e.g., "S3"). This is required for
     * backward compatibility with the legacy node settings format.
     */
    static class SseModePersistor implements NodeParametersPersistor<SSEMode> {

        @Override
        public SSEMode load(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (!settings.containsKey(S3ConnectorNodeSettings.KEY_SSE_MODE)) {
                return SSEMode.S3;
            }
            String key = settings.getString(S3ConnectorNodeSettings.KEY_SSE_MODE);
            return SSEMode.fromKey(key);
        }

        @Override
        public void save(final SSEMode obj, final NodeSettingsWO settings) {
            settings.addString(S3ConnectorNodeSettings.KEY_SSE_MODE, obj.getKey());
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{S3ConnectorNodeSettings.KEY_SSE_MODE}};
        }
    }

    static final class SseEnabledAndKmsModRef implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(SseEnabledRef.class).isTrue()
                .and(i.getEnum(SseModeRef.class).isOneOf(SSEMode.KMS));
        }
    }

    @Effect(predicate = SseEnabledAndKmsModRef.class, type = EffectType.SHOW)
    @ValueReference(KmsKeySettingsRef.class)
    @Layout(ServerSideEncryption.class)
    @Advanced
    KmsKeySettings m_kmsKeySettings = new KmsKeySettings();

    static final class KmsKeySettingsRef implements ParameterReference<KmsKeySettings> {
    }

    @PersistWithin({".."})
    @LoadDefaultsForAbsentFields
    static class KmsKeySettings implements NodeParameters {

        static final class UseAwsManagedKeyRef implements BooleanReference {
        }

        static final class SseEnabledAndNotUseAwsManagedKeyRef implements EffectPredicateProvider {
            @Override
            public EffectPredicate init(final PredicateInitializer i) {
                return i.getPredicate(SseEnabledAndKmsModRef.class)
                    .and(i.getBoolean(UseAwsManagedKeyRef.class).isFalse());
            }
        }

        @Widget(title = "Use default AWS managed key", description = """
                If SSE-KMS is selected as the SSE method, then this option specifies whether or not to encrypt data
                with the default AWS managed CMK.
                """)
        @Persist(configKey = S3ConnectorNodeSettings.KEY_SSE_KMS_USE_AWS_MANAGED)
        @ValueReference(UseAwsManagedKeyRef.class)
        boolean m_useAwsManagedKey = true;

        @Widget(title = "KMS key id", description = """
                If SSE-KMS is selected as the SSE method and the default AWS managed CMK should <b>not</b> be used,
                then this option allows to choose the KMS key with which to encrypt data written to S3. The suggestions
                dropdown fetches the list of available keys (requires permissions <tt>kms:ListKeys</tt>,
                <tt>kms:DescribeKey</tt> and optionally <tt>kms:ListAliases</tt>).
                """)
        @Persist(configKey = S3ConnectorNodeSettings.KEY_SSE_KMS_KEY_ID)
        @Effect(predicate = SseEnabledAndNotUseAwsManagedKeyRef.class, type = EffectType.SHOW)
        @SuggestionsProvider(KmsKeyChoicesProvider.class)
        String m_kmsKeyId = "";
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

    static final class SseEnabledAndCustomerProvidedRef implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(SseEnabledRef.class).isTrue()
                .and(i.getEnum(SseModeRef.class).isOneOf(SSEMode.CUSTOMER_PROVIDED));
        }
    }

    @Effect(predicate = SseEnabledAndCustomerProvidedRef.class, type = EffectType.SHOW)
    @ValueReference(CustomerKeySettingsRef.class)
    @Layout(ServerSideEncryption.class)
    @Advanced
    CustomerKeySettings m_customerKeySettings = new CustomerKeySettings();

    static final class CustomerKeySettingsRef implements ParameterReference<CustomerKeySettings> {
    }

    @PersistWithin({".."})
    @LoadDefaultsForAbsentFields
    static class CustomerKeySettings implements WidgetGroup, Persistable {

        static final class CustomerKeySourceRef implements ParameterReference<CustomerKeySource> {
        }

        static final class SettingsKeySourceRef implements EffectPredicateProvider {
            @Override
            public EffectPredicate init(final PredicateInitializer i) {
                return i.getPredicate(SseEnabledAndCustomerProvidedRef.class)
                    .and(i.getEnum(CustomerKeySourceRef.class).isOneOf(CustomerKeySource.SETTINGS));
            }
        }

        static final class CredentialVarSourceRef implements EffectPredicateProvider {
            @Override
            public EffectPredicate init(final PredicateInitializer i) {
                return i.getPredicate(SseEnabledAndCustomerProvidedRef.class)
                    .and(i.getEnum(CustomerKeySourceRef.class).isOneOf(CustomerKeySource.CREDENTIAL_VAR));
            }
        }

        static final class FileSourceRef implements EffectPredicateProvider {
            @Override
            public EffectPredicate init(final PredicateInitializer i) {
                return i.getPredicate(SseEnabledAndCustomerProvidedRef.class)
                    .and(i.getEnum(CustomerKeySourceRef.class).isOneOf(CustomerKeySource.FILE));
            }
        }

        @Widget(title = "Customer-provided key", description = """
                If SSE-C is selected as the SSE method it is necessary to provide an encryption key. There are multiple
                ways the key could be provided. The base64 encoded key could be entered directly or provided via
                credentials variable using the encoded key as password (username can be anything or left empty). The
                third way is to select a file that contains exactly 32 bytes (256 bit) that should be used as key.
                """)
        @RadioButtonsWidget
        @ValueReference(CustomerKeySourceRef.class)
        @Persistor(CustomerKeySourcePersistor.class)
        CustomerKeySource m_customerKeySource = CustomerKeySource.SETTINGS;

        @Widget(title = "Key", description = "Enter the base64 encoded encryption key.")
        @Effect(predicate = SettingsKeySourceRef.class, type = EffectType.SHOW)
        @Persist(configKey = S3ConnectorNodeSettings.KEY_SSE_CUSTOMER_KEY)
        @ValueProvider(CustomerKeyClearWhenNotSelectedStateProvider.class)
        String m_customerKey = "";

        @Widget(title = "Credential",
            description = "Select the credential flow variable containing the base64 encoded key.")
        @ChoicesProvider(CredentialFlowVariableChoicesProvider.class)
        @Effect(predicate = CredentialVarSourceRef.class, type = EffectType.SHOW)
        @Persist(configKey = S3ConnectorNodeSettings.KEY_SSE_CUSTOMER_KEY_VAR)
        String m_customerKeyVar = "";

        @Widget(title = "File",
            description = "Select a file containing exactly 32 bytes (256 bit) to use as the encryption key.")
        @FileReaderWidget
        @Effect(predicate = FileSourceRef.class, type = EffectType.SHOW)
        @Persistor(CustomerKeyFilePersistor.class)
        FileSelection m_customerKeyFile = new FileSelection();

        /**
         * StateProvider that clears the customer key when it's not the selected option (because it's a "secret" that
         * should not be persisted)
         */
        static final class CustomerKeyClearWhenNotSelectedStateProvider implements StateProvider<String> {

            private Supplier<Boolean> m_sseEnabledSupplier;
            private Supplier<SSEMode> m_sseModeSupplier;
            private Supplier<CustomerKeySource> m_customerKeySourceSupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                m_sseEnabledSupplier = initializer.computeFromValueSupplier(SseEnabledRef.class);
                m_sseModeSupplier = initializer.computeFromValueSupplier(SseModeRef.class);
                m_customerKeySourceSupplier = initializer.computeFromValueSupplier(CustomerKeySourceRef.class);
            }

            @Override
            public String computeState(final NodeParametersInput context) throws StateComputationFailureException {
                if (!Boolean.TRUE.equals(m_sseEnabledSupplier.get()) //
                    || SSEMode.CUSTOMER_PROVIDED != m_sseModeSupplier.get() //
                    || CustomerKeySource.SETTINGS != m_customerKeySourceSupplier.get()) {
                    // "customer-provided" SSE not selected or different source selected - clear key
                    return "";
                }
                throw new StateComputationFailureException(); // leave unchanged
            }
        }

        /** Provider for credential flow variables. */
        static final class CredentialFlowVariableChoicesProvider implements StringChoicesProvider {
            @Override
            public List<String> choices(final NodeParametersInput context) {
                // can't just use 'context.getAvailableInputFlowVariables(VariableType.CredentialsType.INSTANCE)'
                // as this will not provide workflow variables (deprecated concept but still supported in migrations)
                return ((NodeParametersInputImpl)context).getCredentialsProvider() //
                        .map(CredentialsProvider::listNames) //
                        .map(ArrayList::new) //
                        .map(l -> (List<String>)l) //
                        .orElse(List.of());
            }
        }

        static final class CustomerKeySourcePersistor implements NodeParametersPersistor<CustomerKeySource> {

            @Override
            public CustomerKeySource load(final NodeSettingsRO settings)
                throws InvalidSettingsException {
                String sourceKey = settings.getString(S3ConnectorNodeSettings.KEY_SSE_CUSTOMER_KEY_SOURCE, null);
                CustomerKeySource source = CustomerKeySource.fromKey(sourceKey);
                return source != null ? source : CustomerKeySource.SETTINGS;
            }

            @Override
            public void save(final CustomerKeySource param, final NodeSettingsWO settings) {
                settings.addString(S3ConnectorNodeSettings.KEY_SSE_CUSTOMER_KEY_SOURCE, param.getKey());
            }

            @Override
            public String[][] getConfigPaths() {
                return new String[][] { { S3ConnectorNodeSettings.KEY_SSE_CUSTOMER_KEY_SOURCE } };
            }
        }

        static final class CustomerKeyFilePersistor extends LegacyReaderFileSelectionPersistor {

            CustomerKeyFilePersistor() {
                super(S3ConnectorNodeSettings.KEY_SSE_CUSTOMER_KEY_FILE);
            }
        }
    }

    @Override
    public void validate() throws InvalidSettingsException {
        if (m_sseEnabled) {
            if (m_sseMode == SSEMode.KMS && !m_kmsKeySettings.m_useAwsManagedKey) {
                if (StringUtils.isBlank(m_kmsKeySettings.m_kmsKeyId)) {
                    throw new InvalidSettingsException("SSE-KMS key id or AWS default key required.");
                }
            } else if (m_sseMode == SSEMode.CUSTOMER_PROVIDED
                && m_customerKeySettings.m_customerKeySource == CustomerKeySource.SETTINGS) {
                try {
                    AwsUtils.getCustomerKeyBytes(m_customerKeySettings.m_customerKey);
                } catch (IOException ex) {
                    throw new InvalidSettingsException(ex);
                }
            }
        }
    }
}
