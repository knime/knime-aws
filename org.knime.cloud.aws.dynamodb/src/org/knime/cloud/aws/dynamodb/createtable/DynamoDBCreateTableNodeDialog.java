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
package org.knime.cloud.aws.dynamodb.createtable;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import org.knime.cloud.aws.dynamodb.createtable.IndexSettings.IndexType;
import org.knime.cloud.aws.dynamodb.settings.DynamoDBTableSettings;
import org.knime.cloud.aws.dynamodb.ui.AddEmptyRowTableModelListener;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBTablePanel;
import org.knime.cloud.aws.dynamodb.ui.EnumComboBox;
import org.knime.cloud.aws.dynamodb.ui.indexes.GlobalIndexPanel;
import org.knime.cloud.aws.dynamodb.ui.indexes.IndexPanel;
import org.knime.cloud.aws.dynamodb.ui.indexes.LocalIndexPanel;
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
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;
import software.amazon.awssdk.services.dynamodb.model.Tag;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Dialog for the DynamoDB Create Table node.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
final class DynamoDBCreateTableNodeDialog extends NodeDialogPane {

    private static final int MAX_CAPACITY_UNITS = 40000;

    private static final String BILLING_MODE_PROVISIONED = "Provisioned";
    private static final String BILLING_MODE_PAY_PER_REQ = "Pay Per Request";

    private final DynamoDBCreateTableSettings m_settings = new DynamoDBCreateTableSettings();
    private DynamoDBTablePanel m_table;

    private final JTextField m_hashKeyName = new JTextField(10);
    private final EnumComboBox<ScalarAttributeType> m_hashKeyType
        = new EnumComboBox<>(ScalarAttributeType.values(), DynamoDBUtil.getHumanReadableKeyTypes());

    private final JCheckBox m_hasRangeKey = new JCheckBox("Range Key");
    private final JTextField m_rangeKeyName = new JTextField(10);
    private final EnumComboBox<ScalarAttributeType> m_rangeKeyType
        = new EnumComboBox<>(ScalarAttributeType.values(), DynamoDBUtil.getHumanReadableKeyTypes());

    private final EnumComboBox<BillingMode> m_billingMode = new EnumComboBox<>(BillingMode.values(),
            new String[] {BILLING_MODE_PROVISIONED, BILLING_MODE_PAY_PER_REQ});

    private final JSpinner m_readUnits = new JSpinner(new SpinnerNumberModel(5, 1, MAX_CAPACITY_UNITS, 1));
    private final JSpinner m_writeUnits = new JSpinner(new SpinnerNumberModel(5, 1, MAX_CAPACITY_UNITS, 1));

    private final JCheckBox m_blockUntilActive = new JCheckBox("Block until table is active");

    private final DefaultTableModel m_tagsModel = new DefaultTableModel(new String[] {"Name", "Value"}, 1);

    private final JPanel m_indexesTab = new JPanel(new BorderLayout());
    private final JPanel m_indexes = new JPanel();

    // SSE
    private final JCheckBox m_sseEnabled = new JCheckBox("Enable Server-Site Encryption");
    private final JTextField m_kmsMasterKeyId = new JTextField();

    // Streams
    private final JCheckBox m_streamsEnabled = new JCheckBox("Enable Streams");
    private final EnumComboBox<StreamViewType> m_streamType = new EnumComboBox<>(StreamViewType.values(), new String[] {
            "New", "Old", "New and old", "Keys only"
    });

    /**
     * Creates a new instance of the dialog.
     */
    DynamoDBCreateTableNodeDialog() {
        addTab("Standard Settings", createStdSettingsTab());
        addTab("Indexes", createIndexesTab());
        addTab("Tags", createTagsTab());
        addTab("Advanced", createAdvancedTab());
    }

    private JPanel createAdvancedTab() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        panel.add(createSSEPanel(), c);

        c.gridy++;
        panel.add(createStreamsPanel(), c);

        return panel;
    }

    private JPanel createSSEPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Server-Site Encryption"));
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        m_sseEnabled.addActionListener(e -> m_kmsMasterKeyId.setEnabled(m_sseEnabled.isSelected()));
        panel.add(m_sseEnabled, c);

        c.gridy++;
        panel.add(new JLabel("KMS Master Key ID"), c);

        c.gridx++;
        panel.add(m_kmsMasterKeyId, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        final JLabel lbl = new JLabel("(only specify if different from the default DynamoDB customer master key)");
        lbl.setFont(lbl.getFont().deriveFont(10.0f));
        panel.add(lbl, c);

        return panel;
    }

    private JPanel createStreamsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Streams"));
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        m_streamsEnabled.addActionListener(e -> m_streamType.setEnabled(m_streamsEnabled.isSelected()));
        panel.add(m_streamsEnabled, c);

        c.gridy++;
        panel.add(new JLabel("Stream View Type"), c);

        c.gridx++;
        panel.add(m_streamType, c);

        return panel;
    }

    private JPanel createTagsTab() {
        final JPanel panel = new JPanel();

        final JTable tagsTbl = new JTable(m_tagsModel);
        tagsTbl.setPreferredScrollableViewportSize(new Dimension(200, 200));
        m_tagsModel.addTableModelListener(new AddEmptyRowTableModelListener(m_tagsModel));
        final JScrollPane tagsScrollPane = new JScrollPane(tagsTbl);
        panel.add(tagsScrollPane);

        final JButton deleteTag = new JButton("-");
        deleteTag.addActionListener(e -> {
            final int mx = tagsTbl.getSelectionModel().getMaxSelectionIndex();
            final int mn = tagsTbl.getSelectionModel().getMinSelectionIndex();
            for (int i = mx; i >= mn; i--) {
                m_tagsModel.removeRow(i);
            }
            if (m_tagsModel.getRowCount() == 0) {
                m_tagsModel.setRowCount(1);
            }
        });
        panel.add(deleteTag);
        return panel;
    }

    private JPanel createIndexesTab() {
        final JPanel btns = new JPanel();
        final JButton addGlobalIdx = new JButton("Add Global Index");
        final JButton addLocalIdx = new JButton("Add Local Index");
        btns.add(addGlobalIdx);
        btns.add(addLocalIdx);
        m_indexesTab.add(btns, BorderLayout.SOUTH);

        m_indexes.setLayout(new BoxLayout(m_indexes, BoxLayout.Y_AXIS));
        final JScrollPane scrollPane = new JScrollPane(m_indexes);
        m_indexesTab.add(scrollPane, BorderLayout.CENTER);

        addGlobalIdx.addActionListener(e -> {
            m_indexes.add(createGlobalIndexPanel(null));
            m_indexesTab.validate();
        });

        addLocalIdx.addActionListener(e -> {
            m_indexes.add(createLocalIndexPanel(null));
            m_indexesTab.validate();
        });

        return m_indexesTab;
    }

    private IndexPanel createGlobalIndexPanel(final IndexSettings idx) {
        final GlobalIndexPanel ip = new GlobalIndexPanel();
        ip.addRemoveListener(s -> {
            m_indexes.remove(ip);
            m_indexesTab.validate();
            m_indexesTab.repaint();
        });
        ip.setBillingMode(m_billingMode.getSelectedItemValue());

        if (idx != null) {
            ip.updateFromIndexSettings(idx);
        }
        return ip;
    }

    private IndexPanel createLocalIndexPanel(final IndexSettings idx) {
        final LocalIndexPanel ip = new LocalIndexPanel();
        ip.addRemoveListener(s -> {
            m_indexes.remove(ip);
            m_indexesTab.validate();
            m_indexesTab.repaint();
        });

        if (idx != null) {
            ip.updateFromIndexSettings(idx);
        }
        return ip;
    }

    private JPanel createStdSettingsTab() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        m_table = new DynamoDBTablePanel(createFlowVariableModel(DynamoDBTableSettings.CFG_TABLE_NAME, Type.STRING));
        panel.add(m_table, c);

        c.gridy++;
        panel.add(tableSettingsPanel(), c);

        return panel;
    }

    private JPanel tableSettingsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Table settings"));
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;

        panel.add(new JLabel("Hash Key"), c);

        c.gridx++;
        c.gridwidth = 2;
        panel.add(m_hashKeyName, c);

        c.gridwidth = 1;
        c.gridx += 2;
        panel.add(m_hashKeyType, c);

        c.gridx = 0;
        c.gridy++;
        m_hasRangeKey.addActionListener(e -> setRangeKeyEnabled(m_hasRangeKey.isSelected()));
        panel.add(m_hasRangeKey, c);

        c.gridx++;
        c.gridwidth = 2;
        panel.add(m_rangeKeyName, c);

        c.gridwidth = 1;
        c.gridx += 2;
        panel.add(m_rangeKeyType, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("Billing Mode"), c);

        c.gridx++;
        m_billingMode.addActionListener(e -> {
            final BillingMode mode = m_billingMode.getSelectedItemValue();

            setProvisionedThroughputEnabled(mode == BillingMode.PROVISIONED);

            // Update index UI because if billing mode is pay per request, no throughput can be entered
            for (final Component comp : m_indexes.getComponents()) {
                if (comp instanceof GlobalIndexPanel) {
                    ((GlobalIndexPanel)comp).setBillingMode(mode);
                }
            }
        });
        panel.add(m_billingMode, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("Read Units"), c);

        c.gridx++;
        panel.add(m_readUnits, c);

        c.gridx++;
        panel.add(new JLabel("Write Units"), c);

        c.gridx++;
        panel.add(m_writeUnits, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(m_blockUntilActive, c);

        return panel;
    }

    private void setProvisionedThroughputEnabled(final boolean enabled) {
        m_readUnits.setEnabled(enabled);
        m_writeUnits.setEnabled(enabled);
    }

    private void setRangeKeyEnabled(final boolean enabled) {
        m_rangeKeyName.setEnabled(enabled);
        m_rangeKeyType.setEnabled(enabled);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_settings.loadSettingsForDialog(settings);

        m_table.updateFromSettings(m_settings);

        m_hashKeyName.setText(m_settings.getHashKeyName());
        m_hashKeyType.setSelectedItemValue(m_settings.getHashKeyType());

        m_hasRangeKey.setSelected(m_settings.hasRangeKey());
        m_rangeKeyName.setText(m_settings.getRangeKeyName());
        m_rangeKeyType.setSelectedItemValue(m_settings.getRangeKeyType());

        m_billingMode.setSelectedItemValue(m_settings.getBillingMode());
        m_readUnits.setValue(m_settings.getReadUnits());
        m_writeUnits.setValue(m_settings.getWriteUnits());

        m_blockUntilActive.setSelected(m_settings.isBlockUntilActive());

        m_tagsModel.setRowCount(0);
        m_tagsModel.setRowCount(1);
        for (int i = m_settings.getTags().size() - 1; i >= 0; i--) {
            final Tag tag = m_settings.getTags().get(i);
            m_tagsModel.insertRow(0, new String[] {tag.key(), tag.value()});
        }

        m_indexes.removeAll();
        for (final IndexSettings idx : m_settings.getIndexes()) {
            final IndexPanel ip = idx.getType() == IndexType.GLOBAL
                    ? createGlobalIndexPanel(idx) : createLocalIndexPanel(idx);
            m_indexes.add(ip);
        }
        m_indexes.validate();

        final CloudConnectionInformation con = KNIMEUtil.getConnectionInformationInDialog(specs);
        m_table.setRegionOverwrite(Region.of(con.getHost()));

        m_sseEnabled.setSelected(m_settings.isSseEnabled());
        m_kmsMasterKeyId.setText(m_settings.getKmsMasterKeyId());

        m_streamsEnabled.setSelected(m_settings.isStreamsEnabled());
        m_streamType.setSelectedItemValue(m_settings.getStreamViewType());

        m_streamType.setEnabled(m_streamsEnabled.isSelected());
        m_kmsMasterKeyId.setEnabled(m_sseEnabled.isSelected());
        setProvisionedThroughputEnabled(m_settings.getBillingMode() == BillingMode.PROVISIONED);
        setRangeKeyEnabled(m_settings.hasRangeKey());
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_table.saveToSettings(m_settings);

        m_settings.setHashKeyName(m_hashKeyName.getText());
        m_settings.setHashKeyType(m_hashKeyType.getSelectedItemValue());

        m_settings.setHasRangeKey(m_hasRangeKey.isSelected());
        m_settings.setRangeKeyName(m_rangeKeyName.getText());
        m_settings.setRangeKeyType(m_rangeKeyType.getSelectedItemValue());

        m_settings.setBillingMode(m_billingMode.getSelectedItemValue());
        m_settings.setReadUnits((int)m_readUnits.getValue());
        m_settings.setReadUnits((int)m_readUnits.getValue());

        m_settings.setBlockUntilActive(m_blockUntilActive.isSelected());

        m_settings.setSseEnabled(m_sseEnabled.isSelected());
        m_settings.setKmsMasterKeyId(m_kmsMasterKeyId.getName());

        m_settings.setStreamsEnabled(m_streamsEnabled.isSelected());
        m_settings.setStreamViewType(m_streamType.getSelectedItemValue());

        m_settings.getTags().clear();
        for (int i = 0; i < m_tagsModel.getRowCount(); i++) {
            final String name = (String)m_tagsModel.getValueAt(i, 0);
            final String val = (String)m_tagsModel.getValueAt(i, 1);
            if (!StringUtils.isBlank(name) && !StringUtils.isBlank(val)) {
                m_settings.getTags().add(Tag.builder().key(name).value(val).build());
            }
        }

        m_settings.getIndexes().clear();
        for (final Component c : m_indexes.getComponents()) {
            if (c instanceof IndexPanel) {
                final IndexSettings idx = ((IndexPanel)c).createIndexSettings();
                m_settings.getIndexes().add(idx);
            }
        }

        m_settings.saveSettings(settings);
    }
}
