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
 *   1 Jul 2019 (Alexander): created
 */
package org.knime.cloud.aws.dynamodb.updateitem;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.knime.cloud.aws.dynamodb.settings.DynamoDBTableSettings;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBKeyColumnsPanel;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBPlaceholderPanel;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBTablePanel;
import org.knime.cloud.aws.dynamodb.ui.EnumComboBox;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.FlowVariable.Type;

import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

/**
 * Dialog for the DynamoDB Update Item node.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
public class DynamoDBUpdateItemNodeDialog extends NodeDialogPane {

    private final DynamoDBUpdateItemSettings m_settings = new DynamoDBUpdateItemSettings();

    private DynamoDBTablePanel m_table;
    private final DynamoDBKeyColumnsPanel m_keys = new DynamoDBKeyColumnsPanel();
    private final DynamoDBPlaceholderPanel m_placeholders = new DynamoDBPlaceholderPanel(true);

    private final JTextField m_conditionExpression = new JTextField(10);
    private final JTextField m_updateExpression = new JTextField();
    private final EnumComboBox<ReturnValue> m_returnValue = new EnumComboBox<>(ReturnValue.values(),
            new String[] {"None", "All old", "Updated old", "All new", "Updated new"});

    private final JCheckBox m_flowVars = new JCheckBox("Publish consumed capacity units as flow variable");

    /**
     * Creates a new instance of the dialog.
     */
    public DynamoDBUpdateItemNodeDialog() {
        addTab("Standard Settings", createStdSettingsTab());
    }

    private JPanel createStdSettingsTab() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        m_table = new DynamoDBTablePanel(createFlowVariableModel(DynamoDBTableSettings.CFG_TABLE_NAME, Type.STRING));
        panel.add(m_table, c);

        c.gridy++;
        panel.add(m_keys, c);

        c.gridy++;
        c.gridwidth = 1;
        panel.add(new JLabel("Condition Expression"), c);

        c.gridx++;
        panel.add(m_conditionExpression, c);

        c.gridy++;
        c.gridx = 0;
        panel.add(new JLabel("Update Expression"), c);

        c.gridx++;
        panel.add(m_updateExpression, c);

        c.gridy++;
        c.gridx = 0;
        panel.add(new JLabel("Return Values"), c);

        c.gridx++;
        panel.add(m_returnValue, c);

        c.gridwidth = 2;
        c.gridy++;
        c.gridx = 0;
        panel.add(m_placeholders, c);

        c.gridy++;
        panel.add(m_flowVars, c);
        return panel;
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_settings.loadSettingsForDialog(settings);

        m_table.updateFromSettings(m_settings);
        m_keys.updateFromSettings((DataTableSpec)specs[1], m_settings.getKeyColumns());
        m_placeholders.updateFromSettings(m_settings.getPlaceholders(), (DataTableSpec)specs[1]);

        m_conditionExpression.setText(m_settings.getConditionExpression());
        m_updateExpression.setText(m_settings.getUpdateExpression());
        m_returnValue.setSelectedItemValue(m_settings.getReturnValue());
        m_flowVars.setSelected(m_settings.publishConsumedCapUnits());
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_table.saveToSettings(m_settings);
        m_keys.saveToSettings(m_settings.getKeyColumns());
        m_placeholders.saveToSettings(m_settings.getPlaceholders());
        m_settings.setConditionExpression(m_conditionExpression.getText());
        m_settings.setUpdateExpression(m_updateExpression.getText());
        m_settings.setReturnValue(m_returnValue.getSelectedItemValue());
        m_settings.setPublishConsumedCapUnits(m_flowVars.isSelected());

        m_settings.saveSettings(settings);
    }
}
