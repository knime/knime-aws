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
package org.knime.cloud.aws.dynamodb.batchdelete;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
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
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.ColumnFilter;
import org.knime.core.node.util.ColumnSelectionPanel;
import org.knime.core.node.workflow.FlowVariable.Type;

import software.amazon.awssdk.regions.Region;

/**
 * Dialog for the DynamoDB Batch Delete node.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
final class DynamoDBBatchDeleteNodeDialog extends NodeDialogPane {

    private CloudConnectionInformation m_conCredentials = null;

    // Filter for hash and range key columns
    private static final ColumnFilter FILTER = new ColumnFilter() {

        @Override
        public boolean includeColumn(final DataColumnSpec colSpec) {
            return colSpec.getType().isCompatible(StringValue.class)
                    || colSpec.getType().isCompatible(DoubleValue.class);
        }

        @Override
        public String allFilteredMsg() {
            return "No string or number column found";
        }
    };

    private final DynamoDBBatchDeleteSettings m_settings = new DynamoDBBatchDeleteSettings();
    private DynamoDBTablePanel m_table;
    private final JSpinner m_batchSize = new JSpinner(new SpinnerNumberModel(25, 1, 25, 1));
    private final JCheckBox m_flowVars = new JCheckBox("Publish consumed capacity units as flow variable");
    private final ColumnSelectionPanel m_hashColumn = new ColumnSelectionPanel(
            BorderFactory.createEmptyBorder(0, 0, 0, 0), FILTER, false, false);
    private final ColumnSelectionPanel m_rangeColumn = new ColumnSelectionPanel(
            BorderFactory.createEmptyBorder(0, 0, 0, 0), FILTER, true, false);
    private final JCheckBox m_hashKeyBinary = new JCheckBox("as Base64 binary");
    private final JCheckBox m_rangeKeyBinary = new JCheckBox("as Base64 binary");

    /**
     * Creates a new instance of the dialog.
     */
    DynamoDBBatchDeleteNodeDialog() {
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
        stdSettings.add(createColumnSelectionPanel(), c);

        c.gridy++;
        stdSettings.add(m_flowVars, c);

        return stdSettings;
    }

    private boolean mayBeBinaryColumn(final DataColumnSpec colSpec) {
        return colSpec != null && !colSpec.getType().equals(DataType.getMissingCell().getType())
                && colSpec.getType().isCompatible(StringValue.class);
    }

    private JPanel createColumnSelectionPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;

        panel.add(new JLabel("Hash Key Column"), c);
        c.gridx++;
        m_hashColumn.setRequired(true);
        m_hashColumn.addActionListener(e -> {
            m_hashKeyBinary.setEnabled(mayBeBinaryColumn(m_hashColumn.getSelectedColumnAsSpec()));
        });
        panel.add(m_hashColumn, c);

        c.gridx++;
        panel.add(m_hashKeyBinary, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("Range Key Column"), c);
        c.gridx++;
        m_rangeColumn.setRequired(false);
        m_rangeColumn.addActionListener(e -> {
            m_rangeKeyBinary.setEnabled(mayBeBinaryColumn(m_rangeColumn.getSelectedColumnAsSpec()));
        });
        panel.add(m_rangeColumn, c);

        c.gridx++;
        panel.add(m_rangeKeyBinary, c);

        return panel;
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

        m_hashColumn.update((DataTableSpec)specs[1], m_settings.getKeyColumns().getHashKeyColumn());
        m_rangeColumn.update((DataTableSpec)specs[1], m_settings.getKeyColumns().getRangeKeyColumn());
        m_hashKeyBinary.setSelected(m_settings.getKeyColumns().isHashKeyBinary());
        m_rangeKeyBinary.setSelected(m_settings.getKeyColumns().isRangeKeyBinary());
        m_hashKeyBinary.setEnabled(mayBeBinaryColumn(m_hashColumn.getSelectedColumnAsSpec()));
        m_rangeKeyBinary.setEnabled(mayBeBinaryColumn(m_rangeColumn.getSelectedColumnAsSpec()));

        m_conCredentials = KNIMEUtil.getConnectionInformationInDialog(specs);

        m_table.setRegionOverwrite(m_conCredentials == null ? null : Region.of(m_conCredentials.getHost()));
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_table.saveToSettings(m_settings);

        m_settings.setPublishConsumedCapUnits(m_flowVars.isSelected());
        m_settings.setBatchSize((int)m_batchSize.getValue());

        m_settings.getKeyColumns().setHashKeyColumn(m_hashColumn.getSelectedColumn());
        m_settings.getKeyColumns().setRangeKeyColumn(m_rangeColumn.getSelectedColumn());
        m_settings.getKeyColumns().setHashKeyBinary(m_hashKeyBinary.isSelected());
        m_settings.getKeyColumns().setRangeKeyBinary(m_rangeKeyBinary.isSelected());

        m_settings.saveSettings(settings);
    }

}
