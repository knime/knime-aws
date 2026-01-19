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

package org.knime.cloud.aws.nodes.connector;

import java.util.List;

import org.apache.commons.lang3.tuple.Triple;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.legacy.LegacyAuthenticationTypeSelection;
import org.knime.node.parameters.persistence.legacy.LegacyAuthenticationTypeSelection.AuthTypes;
import org.knime.node.parameters.persistence.legacy.LegacyAuthenticationTypeSelection.CredentialsAuth;
import org.knime.node.parameters.persistence.legacy.LegacyAuthenticationTypeSelection.CredentialsAuth.CredentialsAuthModification;
import org.knime.node.parameters.persistence.legacy.LegacyAuthenticationTypeSelection.LegacyAuthenticationModification;
import org.knime.node.parameters.persistence.legacy.LegacyAuthenticationTypeSelection.UserPwdAuth;
import org.knime.node.parameters.persistence.legacy.LegacyAuthenticationTypeSelection.UserPwdAuth.UserPwdAuthModification;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.EnumChoice;
import org.knime.node.parameters.widget.choices.EnumChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;

import software.amazon.awssdk.regions.Region;

/**
 * Node parameters for Amazon Authenticator.
 *
 * @author Sascha Wolke, KNIME GmbH, Berlin, Germany
 */
@SuppressWarnings("restriction")
@LoadDefaultsForAbsentFields
class AmazonAuthenticationNodeParameters implements NodeParameters {

    // ===================== Section definitions =====================

    @Section(title = "Authentication")
    interface AuthenticationSection {
    }

    @Section(title = "Session Token")
    @After(AuthenticationSection.class)
    @Effect(predicate = AuthMethodSupportsSessionTokenPredicate.class, type = EffectType.SHOW)
    interface SessionTokenSection {
    }

    @Section(title = "Switch Role")
    @After(SessionTokenSection.class)
    interface SwitchRoleSection {
    }

    @Section(title = "Region")
    @After(SwitchRoleSection.class)
    interface RegionSection {
    }

    @Section(title = "Advanced")
    @After(RegionSection.class)
    @Advanced
    interface AdvancedSection {
    }

    // ===================== Fields =====================

    @Layout(AuthenticationSection.class)
    @PersistWithin("auth")
    @Modification(AuthTypeSelectionModification.class)
    LegacyAuthenticationTypeSelection m_authType = new LegacyAuthenticationTypeSelection();

    static final class AuthTypeSelectionModification extends LegacyAuthenticationModification {

        protected AuthTypeSelectionModification() {
            super(AuthenticationTypeChoicesProvider.class, "Authentication", getSupportedAuthTypes());
        }

    }

    private static List<Triple<AuthTypes, String, String>> getSupportedAuthTypes() {
        return List.of(
            Triple.of(AuthTypes.CREDENTIALS, "Use credentials",
                    "Authenticate with Access Key ID and Secret Access Key stored in a credentials flow variable."),
            Triple.of(AuthTypes.USER_PWD, "Access Key ID and Secret Access Key",
                    "Access Key ID and Secret Access Key based authentication."),
            Triple.of(AuthTypes.KERBEROS, "Default Credential Provider Chain",
                "Use the Default Credential Provider Chain for authentication."),
            Triple.of(AuthTypes.NONE, "Anonymous Credentials",
                    "Use anonymous credentials to make anonymous requests to an Amazon service"));
    }

    @Layout(AuthenticationSection.class)
    @PersistWithin("auth")
    @Modification(AccessKeyIDAndSecretModification.class)
    UserPwdAuth m_accessKeyIDAndSecretKey = new UserPwdAuth();

    static final class AccessKeyIDAndSecretModification extends UserPwdAuthModification {

        protected AccessKeyIDAndSecretModification() {
            super("Access Key ID and Secret Access Key", """
                Authentication settings for AWS access key ID and secret access key.
                Select "Use credentials" as authentication method to provide the
                access key ID and secret via a credentials flow variable.
                """, "Access key ID", "Secret access key");
        }

    }

    @Layout(AuthenticationSection.class)
    @PersistWithin("auth")
    CredentialsAuth m_credentialsAuth = new CredentialsAuth();

    static final class CredentialAuthenticationModification extends CredentialsAuthModification {

        protected CredentialAuthenticationModification() {
            super("Use credentials", "Use credentials from a flow variable.");
        }

    }

    @Layout(SessionTokenSection.class)
    @Persist(configKey = "useSessionToken")
    @Widget(title = "Use Session Token",
        description = "Use a session token for temporary credential authentication.")
    @ValueReference(UseSessionTokenRef.class)
    boolean m_useSessionToken;

    static final class UseSessionTokenRef implements BooleanReference {
    }

    @Layout(SessionTokenSection.class)
    @Persist(configKey = "sessionToken")
    @Widget(title = "Session Token", description = "The session token for temporary credential authentication.")
    @Effect(predicate = UseSessionTokenRef.class, type = EffectType.SHOW)
    String m_token = "";

    @Layout(SwitchRoleSection.class)
    @Persist(configKey = "switchRole")
    @Widget(title = "Switch Role",
        description = """
                Switch your IAM role. For more information see:
                <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role_console.html">\
                Switching to a Role</a>
                """)
    @ValueReference(SwitchRoleRef.class)
    boolean m_switchRole;

    static final class SwitchRoleRef implements BooleanReference {
    }

    @Layout(SwitchRoleSection.class)
    @Persist(configKey = "switchRoleAccount")
    @Widget(title = "Account", description = "The 12-digit account ID for which the role should be assumed.")
    @Effect(predicate = SwitchRoleRef.class, type = EffectType.SHOW)
    String m_roleAccount = "";

    @Layout(SwitchRoleSection.class)
    @Persist(configKey = "switchRoleName")
    @Widget(title = "Role", description = "The name of the role that should be assumed.")
    @Effect(predicate = SwitchRoleRef.class, type = EffectType.SHOW)
    String m_roleName = "";

    @Layout(RegionSection.class)
    @Persist(configKey = "region")
    @Widget(title = "Region", description = "The Amazon services geographical region.")
    @ChoicesProvider(RegionChoicesProvider.class)
    String m_region = "us-east-1";

    @Layout(AdvancedSection.class)
    @Persist(configKey = "timeout")
    @Widget(title = "Timeout",
        description = "The timeout in milliseconds when initially establishing a connection.")
    @NumberInputWidget(stepSize = 100, minValidation = IsNonNegativeValidation.class)
    int m_timeout = 30000;

    // ===================== Predicates =====================

    static final class AuthMethodSupportsSessionTokenPredicate implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(LegacyAuthenticationTypeSelection.SelectedAuthenticationTypeRef.class)
                    .isOneOf(AuthTypes.USER_PWD, AuthTypes.CREDENTIALS);
        }

    }

    // ===================== Choices Providers =====================

    static final class AuthenticationTypeChoicesProvider implements EnumChoicesProvider<AuthTypes> {

        @Override
        public List<EnumChoice<AuthTypes>> computeState(final NodeParametersInput context) {
            return getSupportedAuthTypes().stream()
                    .map(triple -> new EnumChoice<>(triple.getLeft(), triple.getMiddle())).toList();
        }

    }

    static final class RegionChoicesProvider implements StringChoicesProvider {

        @Override
        public List<String> choices(final NodeParametersInput context) {
            return Region.regions().stream().map(Region::id).sorted().toList();
        }

    }

}
