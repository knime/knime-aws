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

import org.knime.cloud.aws.filehandling.s3.fs.api.S3FSConnectionConfig;
import org.knime.cloud.aws.filehandling.s3.node.S3GenericConnectorNodeParameters.RemoveAmazonS3ConnectionInfoMessage;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.LegacyCredentials;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.LegacyCredentialsAuthProviderSettings;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification.WidgetGroupModifier;
import org.knime.filehandling.core.connections.base.auth.UserPasswordAuthProviderSettings;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
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
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.credentials.Credentials;
import org.knime.node.parameters.widget.credentials.CredentialsWidget;
import org.knime.node.parameters.widget.message.TextMessage;
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
@SuppressWarnings("restriction")
@Modification(RemoveAmazonS3ConnectionInfoMessage.class)
final class S3GenericConnectorNodeParameters extends S3ConnectorNodeParameters {

    static final class RemoveAmazonS3ConnectionInfoMessage implements Modification.Modifier {

        @Override
        public void modify(final WidgetGroupModifier group) {
            group.find(InfoMessageRef.class).removeAnnotation(TextMessage.class);
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
    String m_endpointUrl = "";

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
    boolean m_pathStyle = true;

    @Persist(configKey = "region")
    @Advanced
    @Layout(EndpointSettings.class)
    @Widget(title = "Region", description = """
            Optional region to set on the client. Might be empty, depending on how your S3-compatible endpoint is
            set up.
            """)
    String m_region = "";

    @Persist(configKey = "auth")
    @Layout(AuthenticationSettings.class)
    AuthenticationParameters m_authParameters = new AuthenticationParameters();

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
