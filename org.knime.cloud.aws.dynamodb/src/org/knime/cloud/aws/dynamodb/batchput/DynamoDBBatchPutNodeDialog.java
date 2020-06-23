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
package org.knime.cloud.aws.dynamodb.batchput;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.knime.cloud.aws.dynamodb.settings.DynamoDBTableSettings;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBTablePanel;
import org.knime.cloud.aws.dynamodb.utils.DynamoDBUtil;
import org.knime.cloud.aws.dynamodb.utils.KNIMEUtil;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.FlowVariable.Type;

import software.amazon.awssdk.regions.Region;

/**
 * Dialog for the DynamoDB Batch Put node.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
final class DynamoDBBatchPutNodeDialog extends NodeDialogPane {

    private CloudConnectionInformation m_conCredentials = null;

    private final DynamoDBBatchPutSettings m_settings = new DynamoDBBatchPutSettings();
    private DynamoDBTablePanel m_table;
    private final JSpinner m_batchSize = new JSpinner(new SpinnerNumberModel(25, 1, 25, 1));
    private final JCheckBox m_flowVars = new JCheckBox("Publish consumed capacity units as flow variable");

    /**
     * Creates a new instance of the dialog.
     */
    DynamoDBBatchPutNodeDialog() {
        addTab("Standard Settings", createStdSettingsTab());
    }

    private JPanel createStdSettingsTab() {
        final JPanel stdSettings = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        m_table = new DynamoDBTablePanel(createFlowVariableModel(DynamoDBTableSettings.CFG_TABLE_NAME, Type.STRING),
                this::getTableNames);
        stdSettings.add(m_table, c);

        c.gridy++;
        stdSettings.add(createBatchWriteSettingsPanel(), c);

        c.gridy++;
        stdSettings.add(m_flowVars, c);

        return stdSettings;
    }

    private JPanel createBatchWriteSettingsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;

        panel.add(new JLabel("Batch Size"), c);
        c.weightx = 0;
        c.gridx++;
        panel.add(m_batchSize, c);
        return panel;
    }

    private List<String> getTableNames() {
        try {
            return DynamoDBUtil.getTableNames(m_conCredentials, 20);
        } catch (final Exception e1) {
            return null;
        }
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_settings.loadSettingsForDialog(settings);

        m_table.updateFromSettings(m_settings);

        m_flowVars.setSelected(m_settings.publishConsumedCapUnits());
        m_batchSize.setValue(m_settings.getBatchSize());

        m_conCredentials = KNIMEUtil.getConnectionInformationInDialog(specs);

        m_table.setRegionOverwrite(m_conCredentials == null ? null : Region.of(m_conCredentials.getHost()));
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {

        m_settings.setPublishConsumedCapUnits(m_flowVars.isSelected());

        m_settings.setTableName(m_table.getTableName());
        m_settings.setEndpoint(m_table.getEndpoint());
        m_settings.setRegion(m_table.getRegion());

        m_settings.setBatchSize((int)m_batchSize.getValue());

        m_settings.saveSettings(settings);
    }

}
