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
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.cloud.aws.filehandling.s3.fs.S3FSConnection;
import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig;
import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig.SSEMode;
import org.knime.cloud.aws.filehandling.s3.node.AbstractS3ConnectorNodeParameters.KmsKeySettings.KmsKeyIdModeRef;
import org.knime.cloud.aws.filehandling.s3.node.S3GenericConnectorNodeParameters.AuthenticationParameters.AuthenticationMethod;
import org.knime.cloud.aws.filehandling.s3.node.S3GenericConnectorNodeParameters.GenericS3ConnectorModification;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.NodeParametersInputImpl;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FSConnectionProvider;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.WithCustomFileSystem;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.LegacyCredentials;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.LegacyCredentialsAuthProviderSettings;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification.WidgetGroupModifier;
import org.knime.filehandling.core.connections.base.auth.UserPasswordAuthProviderSettings;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Before;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.SuggestionsProvider;
import org.knime.node.parameters.widget.credentials.Credentials;
import org.knime.node.parameters.widget.credentials.CredentialsWidget;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.Message;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;
import org.knime.node.parameters.widget.text.TextInputWidget;

/**
 * Node parameters for Generic S3 Connector.
 *
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
@Modification(GenericS3ConnectorModification.class)
@SuppressWarnings("restriction")
final class S3GenericConnectorNodeParameters extends AbstractS3ConnectorNodeParameters {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(S3GenericConnectorNodeParameters.class);

    static final class GenericS3ConnectorModification implements Modification.Modifier {

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

    static final class IntermediateConnectionStateProvider
        implements StateProvider<MessageAndData<CloudConnectionInformation>> {

        Supplier<AuthenticationParameters> m_authParamsSupplier;
        Supplier<Integer> m_socketTimeoutSupplier;
        Supplier<String> m_regionSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_authParamsSupplier = initializer.computeFromValueSupplier(AuthParameterRef.class);
            m_socketTimeoutSupplier = initializer.computeFromValueSupplier(SocketTimeoutRef.class);
            m_regionSupplier = initializer.computeFromValueSupplier(RegionRef.class);
        }

        @Override
        public MessageAndData<CloudConnectionInformation> computeState(final NodeParametersInput context) {
            final var authParams = m_authParamsSupplier.get();
            if (authParams == null) {
                return newNoConnectionInfoMessage("Missing authentication parameters.");
            }

            final var connInfo = new CloudConnectionInformation();
            connInfo.setProtocol("s3");
            connInfo.setTimeout(m_socketTimeoutSupplier.get() * 1000);  // timeout in milliseconds

            final var region = m_regionSupplier.get();
            if (region != null && !region.isEmpty()) {
                connInfo.setHost(region);
            }

            if (authParams.m_type == AuthenticationMethod.ANONYMOUS) {
                connInfo.setUseAnonymous(true);
            } else if (authParams.m_type == AuthenticationMethod.ACCESS_KEY) {
                final var credentialsProviderOpt = ((NodeParametersInputImpl)context).getCredentialsProvider();
                final var credentialsProvider = credentialsProviderOpt.orElseThrow(() -> new IllegalStateException(
                        "No credentials provider available in workflow context."));
                connInfo.setUser(authParams.m_credentials.toCredentials(credentialsProvider).getUsername());
                connInfo.setPassword(authParams.m_credentials.toCredentials(credentialsProvider).getPassword());
            } else if (authParams.m_type == AuthenticationMethod.DEFAULT_PROVIDER_CHAIN) {
                connInfo.setUseKerberos(true);
            } else {
                return newNoConnectionInfoMessage("Invalid authentication method.");
            }
            return new MessageAndData<>(null, connInfo);
        }

        static MessageAndData<CloudConnectionInformation> newNoConnectionInfoMessage(final String msg) {
            return new MessageAndData<>(new Message("No connection to AWS", msg, MessageType.INFO), null);
        }

    }

    /**
     * Provides a file system connection for browsing S3 directories.
     * The connection is constructed from:
     * - CloudConnectionInformation from the shared IntermediateConnectionStateProvider
     * - Current user configuration (socket timeout, normalization, SSE settings, end point URL and path style config)
     *
     * All relevant settings are captured via ValueReferences and used to create a complete S3FSConnectionConfig for
     * proper file browsing behavior. Returns null when no connection is available
     * (error is shown via S3InfoMessageProvider).
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
        private Supplier<String> m_endpointUrlSupplier;
        private Supplier<Boolean> m_pathStyleSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_connectionStateSupplier = initializer.computeFromProvidedState(IntermediateConnectionStateProvider.class);
            // Use getValueSupplier() to register as dependencies (not triggers) - on demand only
            m_socketTimeoutSupplier = initializer.getValueSupplier(SocketTimeoutRef.class);
            m_normalizePathsSupplier = initializer.getValueSupplier(NormalizePathsRef.class);
            m_sseEnabledSupplier = initializer.getValueSupplier(SseEnabledRef.class);
            m_sseModeSupplier = initializer.getValueSupplier(SseModeRef.class);
            m_kmsKeySettingsSupplier = initializer.getValueSupplier(KmsKeySettingsRef.class);
            m_endpointUrlSupplier = initializer.getValueSupplier(EndpointUrlRef.class);
            m_pathStyleSupplier = initializer.getValueSupplier(PathStyleRef.class);
        }

        @Override
        public FSConnectionProvider computeState(final NodeParametersInput context) {
            // Get connection info from shared provider
            var connState = m_connectionStateSupplier.get();
            final CloudConnectionInformation connInfo = connState.data();
            if (connInfo == null) {
                return () -> { throw new IOException("No connection to S3 could be established."); };
            }

            String endpointUrl = m_endpointUrlSupplier.get();
            if (endpointUrl == null || endpointUrl.isEmpty()) {
                return () -> {
                    throw new InvalidSettingsException("URL required on endpoint override.");
                };
            }
            try {
                new URI(endpointUrl); // NOSONAR just checking syntax
            } catch (final URISyntaxException ex) {
                return () -> {
                    throw new InvalidSettingsException("Invalid endpoint URL: " + ex.getMessage(), ex);
                };
            }

            boolean usePathStyle = m_pathStyleSupplier.get();
            int socketTimeout = m_socketTimeoutSupplier.get();
            boolean normalizePaths = m_normalizePathsSupplier.get();
            boolean sseEnabled = m_sseEnabledSupplier.get();
            SSEMode sseMode = m_sseModeSupplier.get();
            KmsKeySettings kmsKeySettings = m_kmsKeySettingsSupplier.get();
            final S3FSConnectionConfig config = createS3ConnectionConfig(connInfo, socketTimeout, normalizePaths,
                sseEnabled, sseMode, kmsKeySettings, endpointUrl, usePathStyle);

            return () -> new S3FSConnection(config);
        }

        /**
         * Creates S3FSConnectionConfig from connection info and all user settings.
         * This mirrors the logic from S3GenericConnectorNodeSettings.toFSConnectionConfig().
         */
        private static S3FSConnectionConfig createS3ConnectionConfig( // NOSONAR
            final CloudConnectionInformation connInfo, //
            final int socketTimeout, //
            final boolean normalizePaths, //
            final boolean sseEnabled, //
            final SSEMode sseMode, //
            final KmsKeySettings kmsKeySettings, //
            final String endpointUrl, //
            final boolean usePathStyle) {

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

            config.setOverrideEndpoint(true);
            config.setEndpointUrl(URI.create(endpointUrl));
            config.setPathStyle(usePathStyle);

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

    @Before(AuthenticationSettings.class)
    @Section(title = "Endpoint Settings")
    interface EndpointSettings {
    }

    @Before(FileSystemSettings.class)
    @Section(title = "Authentication Settings")
    interface AuthenticationSettings {
    }

    @Persist(configKey = "endpointURL")
    @Layout(EndpointSettings.class)
    @Widget(title = "Endpoint", description = "<tt>http(s)</tt> URL of the S3-compatible service endpoint.")
    @TextInputWidget(placeholder = "e.g. https://s3.us-west-2.amazonaws.com/")
    @ValueReference(EndpointUrlRef.class)
    String m_endpointUrl = "";

    static final class EndpointUrlRef implements ParameterReference<String> {
    }

    @Persist(configKey = "pathStyle")
    @Advanced
    @Widget(title = "Use path-style requests", description = """
            If chosen, buckets will be accessed by appending their name to the path of the endpoint URL. Otherwise, they
            will be accessed by prepending their name as a subdomain in the URL hostname. The correct choice depends on
            how your S3-compatible endpoint is set up, but in most cases you will want to use path-style requests. For
            further explanation see the
            <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/VirtualHosting.html">AWS documentation</a>.
            """)
    @Layout(EndpointSettings.class)
    @ValueReference(PathStyleRef.class)
    boolean m_pathStyle = true;

    static final class PathStyleRef implements BooleanReference {
    }

    @Persist(configKey = "region")
    @Advanced
    @Layout(EndpointSettings.class)
    @Widget(title = "Region", description = """
            Optional region to set on the client. Might be empty, depending on how your S3-compatible endpoint is
            set up.
            """)
    @ValueReference(RegionRef.class)
    String m_region = "";

    static final class RegionRef implements ParameterReference<String> {
    }

    @Persist(configKey = "auth")
    @Layout(AuthenticationSettings.class)
    @ValueReference(AuthParameterRef.class)
    AuthenticationParameters m_authParameters = new AuthenticationParameters();

    static final class AuthParameterRef implements ParameterReference<AuthenticationParameters> {
    }

    // for mocking in tests
    static LegacyCredentials getDefaultLegacyCredentials() {
        return new LegacyCredentials(new Credentials());
    }

    @LoadDefaultsForAbsentFields
    static class AuthenticationParameters implements NodeParameters {

        static final String CFG_KEY_ACCESS_KEY_V2 = "accessAndSecretKeyV2";
        static final String CFG_KEY_ANONYMOUS = "anonymous";
        static final String CFG_KEY_ACCESS_KEY = "accessAndSecretKey";
        static final String CFG_KEY_DEFAULT_PROVIDER_CHAIN = "defaultCredProviderChain";

        @Widget(title = "Authentication method", description = "Method of authentication to use.")
        @Persistor(AuthenticationMethodPersistor.class)
        @ValueReference(AuthTypeRef.class)
        AuthenticationMethod m_type = AuthenticationMethod.ACCESS_KEY;

        static final class AuthTypeRef implements ParameterReference<AuthenticationMethod> {
        }

        @Widget(title = "Access key ID & secret key", description = """
                Provide the access key ID and secret key to authenticate against the S3-compatible endpoint.
                The credentials will be stored in weakly encrypted form as part of the node settings.
                (Use credentials flow variables to prevent this.)
                """)
        @CredentialsWidget(usernameLabel = "Access key ID", passwordLabel = "Secret key")
        @Persist(configKey = CFG_KEY_ACCESS_KEY_V2)
        @Migration(LoadFromUserPwdAuthMigration.class)
        @Effect(predicate = IsAccessKeyAuth.class, type = EffectType.SHOW)
        @ValueReference(UserPasswordRef.class)
        LegacyCredentials m_credentials = getDefaultLegacyCredentials();

        interface UserPasswordRef extends ParameterReference<LegacyCredentials> {
        }

        static final class LoadFromUserPwdAuthMigration
            extends LegacyCredentialsAuthProviderSettings.FromUserPasswordAuthProviderSettingsMigration {

            protected LoadFromUserPwdAuthMigration() {
                super(new UserPasswordAuthProviderSettings(
                    S3GenericConnectorNodeSettings.ACCESS_KEY_AND_SECRET_AUTH, true));
            }

        }

        static final class IsAccessKeyAuth implements EffectPredicateProvider {

            @Override
            public EffectPredicate init(final PredicateInitializer i) {
                return i.getEnum(AuthTypeRef.class).isOneOf(AuthenticationMethod.ACCESS_KEY);
            }

        }

        static final class AuthenticationMethodPersistor implements NodeParametersPersistor<AuthenticationMethod> {

            private static final String KEY = "type";

            @Override
            public AuthenticationMethod load(final NodeSettingsRO settings) throws InvalidSettingsException {
                final var typeString = settings.getString(KEY, CFG_KEY_ACCESS_KEY);

                return switch (typeString) {
                    case CFG_KEY_ANONYMOUS -> AuthenticationMethod.ANONYMOUS;
                    case CFG_KEY_ACCESS_KEY, CFG_KEY_ACCESS_KEY_V2 -> AuthenticationMethod.ACCESS_KEY;
                    case CFG_KEY_DEFAULT_PROVIDER_CHAIN -> AuthenticationMethod.DEFAULT_PROVIDER_CHAIN;
                    default -> throw new InvalidSettingsException(
                        String.format("Unknown authentication method: '%s'. Possible values: '%s', '%s', '%s'",
                            typeString, CFG_KEY_ACCESS_KEY_V2, CFG_KEY_ANONYMOUS, CFG_KEY_DEFAULT_PROVIDER_CHAIN));
                };
            }

            @Override
            public void save(final AuthenticationMethod param, final NodeSettingsWO settings) {
                switch (param) { // NOSONAR
                    case ANONYMOUS -> settings.addString(KEY, CFG_KEY_ANONYMOUS);
                    case ACCESS_KEY -> settings.addString(KEY, CFG_KEY_ACCESS_KEY_V2);
                    case DEFAULT_PROVIDER_CHAIN -> settings.addString(KEY, CFG_KEY_DEFAULT_PROVIDER_CHAIN);
                }
                settings.addNodeSettings(CFG_KEY_ANONYMOUS);
                settings.addNodeSettings(CFG_KEY_DEFAULT_PROVIDER_CHAIN);
            }

            @Override
            public String[][] getConfigPaths() {
                return new String[][]{{KEY}};
            }

        }

        enum AuthenticationMethod {
            @Label(value = "Access key ID and secret key", description = """
                    Use an access key ID and secret to authenticate. Check <i>Use credentials</i> to select a
                    credentials flow variable to supply the ID and secret.
                    """)
            ACCESS_KEY, //
            @Label(value = "Anonymous", description = """
                    Use anonymous credentials to make anonymous requests to the S3-compatible endpoint.
                    """)
            ANONYMOUS, //
            @Label(value = "Default credential provider chain", description = """
                    Supply credentials using environment variables or a credentials configuration file. For further
                    details see the <a href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/
                    credentials.html#credentials-chain">AWS documentation</a>.
                    """)
            DEFAULT_PROVIDER_CHAIN;
        }

    }

    @Persist(configKey = "connectionTimeoutInSeconds")
    @Layout(ConnectionSettings.class)
    @Widget(title = "Connection timeout in seconds", description = """
            The amount of time to wait when initially establishing a connection before giving up and timing out. For
            further details see the <a href="https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/
            ClientConfiguration.html#setConnectionTimeout-int-">AWS documentation</a>.
            """)
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class, stepSize = 10)
    int m_connectionTimeout = S3FSConnectionConfig.DEFAULT_CONNECTION_TIMEOUT_SECONDS;

}
