/*
 * ------------------------------------------------------------------------
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

package org.knime.cloud.aws.dynamodb.query;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import org.knime.cloud.aws.dynamodb.settings.DynamoDBTableSettings;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBFilterAndProjectPanel;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBTablePanel;
import org.knime.cloud.aws.dynamodb.ui.EnumComboBox;
import org.knime.cloud.aws.dynamodb.ui.indexes.IndexSelectionPanel;
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
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * Dialog for the DynamoDB Query node.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
final class DynamoDBQueryNodeDialog extends NodeDialogPane {

    private static final String[] OPERATORS = new String[] {"=", "<", "<=", ">", ">=", "BETWEEN"};

    private final DynamoDBQuerySettings m_settings = new DynamoDBQuerySettings();

    private DynamoDBTablePanel m_table;

    private final DynamoDBFilterAndProjectPanel m_fp = new DynamoDBFilterAndProjectPanel();

    private CloudConnectionInformation m_conCredentials = null;

    // Query
    private final JTextField m_hashKeyName = new JTextField(10);
    private final JTextField m_hashKeyValue = new JTextField(10);
    private final EnumComboBox<ScalarAttributeType> m_hashKeyType
        = new EnumComboBox<>(ScalarAttributeType.values(), DynamoDBUtil.getHumanReadableKeyTypes());
    private final JTextField m_rangeKeyName = new JTextField(10);
    private final JTextField m_rangeKeyValue1 = new JTextField(10);
    private final JTextField m_rangeKeyValue2 = new JTextField(10);
    private final JComboBox<String> m_rangeKeyOperator = new JComboBox<>(OPERATORS);
    private final EnumComboBox<ScalarAttributeType> m_rangeKeyType
        = new EnumComboBox<>(ScalarAttributeType.values(), DynamoDBUtil.getHumanReadableKeyTypes());
    private final JLabel m_rangeAndLabel = new JLabel(" AND ");
    private final JCheckBox m_rangeKeyCheckbox = new JCheckBox("Range Key");
    private final JButton m_fetchInfoBtn = new JButton("Fetch info");
    private final JCheckBox m_consistentRead = new JCheckBox("Consistent Read");
    private final JSpinner m_limit = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    private final JCheckBox m_flowVars = new JCheckBox("Publish consumed capacity units as flow variable");
    private final JCheckBox m_scanForward = new JCheckBox("Forward Scan");

    private IndexSelectionPanel m_indexSelectionPanel;
    /**
     * Creates a new instance of the dialog.
     */
    DynamoDBQueryNodeDialog() {
        addTab("Standard Settings", createStdSettingsTab());
        addTab("Filter & Projection", createAdvancedSettingsTab());
    }

    private JPanel createStdSettingsTab() {
        final JPanel stdSettings = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        m_table = new DynamoDBTablePanel(
                createFlowVariableModel(DynamoDBTableSettings.CFG_TABLE_NAME, Type.STRING),
                this::getTableNames);
        m_table.addChangeListener(e -> toggleFetchButton());
        stdSettings.add(m_table, c);

        c.gridy++;
        stdSettings.add(createQueryPanel(), c);

        c.gridy++;
        stdSettings.add(m_flowVars, c);

        return stdSettings;
    }

    private JPanel createAdvancedSettingsTab() {
        final JPanel advSettings = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        advSettings.add(m_fp, c);
        return advSettings;
    }

    private JPanel createQueryPanel() {
        final JPanel kcPanel = new JPanel(new GridBagLayout());
        final GridBagConstraints kc = new GridBagConstraints();
        kc.insets = new Insets(2, 2, 2, 2);
        kc.gridx = 0;
        kc.gridy = 0;
        kc.weightx = 1;
        kc.anchor = GridBagConstraints.WEST;

        m_indexSelectionPanel = new IndexSelectionPanel(this::getIndexNames);
        kcPanel.add(m_indexSelectionPanel, kc);

        kc.gridy++;
        kcPanel.add(new JLabel("Hash Key"), kc);

        kc.gridy++;
        final JPanel hash = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hash.add(m_hashKeyName);
        hash.add(m_hashKeyType);
        hash.add(new JLabel("="));
        hash.add(m_hashKeyValue);
        hash.add(m_fetchInfoBtn);
        kcPanel.add(hash, kc);

        m_fetchInfoBtn.addActionListener(e -> fetchInfo());

        kc.gridy++;
        kcPanel.add(m_rangeKeyCheckbox, kc);

        final JPanel range = new JPanel(new FlowLayout(FlowLayout.LEFT));
        range.add(m_rangeKeyName);
        range.add(m_rangeKeyType);
        range.add(m_rangeKeyOperator);
        range.add(m_rangeKeyValue1);
        range.add(m_rangeAndLabel);
        range.add(m_rangeKeyValue2);

        kc.gridy++;
        kcPanel.add(range, kc);

        kc.gridy++;
        final JPanel limit = new JPanel(new FlowLayout(FlowLayout.LEFT));
        limit.add(new JLabel("Limit (0 = all)"));
        limit.add(m_limit);
        kcPanel.add(limit, kc);

        kc.weightx = 1;
        kc.gridy++;
        kcPanel.add(m_scanForward, kc);

        kc.gridy++;
        kcPanel.add(m_consistentRead, kc);

        m_rangeKeyCheckbox.addActionListener(e -> setRangeKeyEnabled(m_rangeKeyCheckbox.isSelected()));

        m_rangeKeyOperator.addActionListener(
                e -> setBetweenVisible(((String) m_rangeKeyOperator.getSelectedItem()).equals("BETWEEN")));

        return kcPanel;
    }

    private List<String> getIndexNames() {
        try {
            final TableDescription td = DynamoDBUtil.describeTable(m_table.getTableName(), m_conCredentials);
            final List<GlobalSecondaryIndexDescription> gi = td.globalSecondaryIndexes();
            final List<LocalSecondaryIndexDescription> li = td.localSecondaryIndexes();
            final List<String> indexNames =
            		gi.stream().map(GlobalSecondaryIndexDescription::indexName).collect(Collectors.toList());
            indexNames.addAll(li.stream().map(LocalSecondaryIndexDescription::indexName).collect(Collectors.toList()));
            return indexNames;
        } catch (final Exception e1) {
            return null;
        }
    }

    private List<String> getTableNames() {
        try {
            return DynamoDBUtil.getTableNames(m_conCredentials, 20);
        } catch (final Exception e1) {
            return null;
        }
    }

    private void fetchInfo() {
        final boolean rangeKeyEnabled = m_rangeKeyName.isEnabled();
        setQueryFieldsEnabled(false);
        new Thread(() -> {
            TableDescription res = null;
            try {
                res = DynamoDBUtil.describeTable(m_table.getTableName(), m_conCredentials);
            } catch (final Exception e1) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(m_fetchInfoBtn),
                            e1.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
                return;
            }
            final TableDescription table = res;
            SwingUtilities.invokeLater(() -> {
                String hashKeyName = null;
                String rangeKeyName = null;
                if (m_indexSelectionPanel.isUseIndex()) {
                    for (final KeySchemaElement key : table.keySchema()) {
                        if (key.keyType() == KeyType.HASH) {
                            m_hashKeyName.setText(key.attributeName());
                            hashKeyName = key.attributeName();
                        } else {
                            m_rangeKeyName.setText(key.attributeName());
                            rangeKeyName = key.attributeName();
                        }
                    }
                } else {
                    for (final GlobalSecondaryIndexDescription gsid : table.globalSecondaryIndexes()) {
                        if (gsid.indexName().equals(m_indexSelectionPanel.getIndexName())) {
                            for (final KeySchemaElement key : gsid.keySchema()) {
                                if (key.keyType() == KeyType.HASH) {
                                    m_hashKeyName.setText(key.attributeName());
                                    hashKeyName = key.attributeName();
                                } else {
                                    m_rangeKeyName.setText(key.attributeName());
                                    rangeKeyName = key.attributeName();
                                }
                            }
                            break;
                        }
                    }
                    for (final LocalSecondaryIndexDescription lsid : table.localSecondaryIndexes()) {
                        if (lsid.indexName().equals(m_indexSelectionPanel.getIndexName())) {
                            for (final KeySchemaElement key : lsid.keySchema()) {
                                if (key.keyType() == KeyType.HASH) {
                                    m_hashKeyName.setText(key.attributeName());
                                    hashKeyName = key.attributeName();
                                } else {
                                    m_rangeKeyName.setText(key.attributeName());
                                    rangeKeyName = key.attributeName();
                                }
                            }
                            break;
                        }
                    }
                }
                if (hashKeyName != null) {
                    for (final AttributeDefinition def : table.attributeDefinitions()) {
                        if (def.attributeName().equals(hashKeyName)) {
                            m_hashKeyType.setSelectedItemValue(def.attributeType());
                        } else if (def.attributeName().equals(rangeKeyName)) {
                            m_rangeKeyType.setSelectedItemValue(def.attributeType());
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(m_fetchInfoBtn),
                            "Could not find a hash key for the table or index.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
                setQueryFieldsEnabled(true);
                m_rangeKeyName.setEnabled(rangeKeyEnabled);
                m_rangeKeyType.setEnabled(rangeKeyEnabled);
            });
        }).start();
    }

    private void setQueryFieldsEnabled(final boolean enabled) {
        m_hashKeyName.setEnabled(enabled);
        m_hashKeyType.setEnabled(enabled);
        m_rangeKeyName.setEnabled(enabled);
        m_rangeKeyType.setEnabled(enabled);
    }

    private void setBetweenVisible(final boolean visible) {
        m_rangeAndLabel.setVisible(visible);
        m_rangeKeyValue2.setVisible(visible);
    }

    private void setRangeKeyEnabled(final boolean enabled) {
        m_rangeKeyName.setEnabled(enabled);
        m_rangeKeyType.setEnabled(enabled);
        m_rangeKeyOperator.setEnabled(enabled);
        m_rangeKeyValue1.setEnabled(enabled);
        m_rangeAndLabel.setEnabled(enabled);
        m_rangeKeyValue2.setEnabled(enabled);
    }

    private void toggleFetchButton() {
        m_fetchInfoBtn.setEnabled(m_conCredentials != null && m_table.getTableName().length() > 0);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
    	m_conCredentials = KNIMEUtil.getConnectionInformationInDialog(specs);
        m_settings.loadSettingsForDialog(settings);

        m_scanForward.setSelected(m_settings.scanIndexForward());
        m_limit.setValue(m_settings.getLimit());
        m_indexSelectionPanel.update(m_settings.getIndexName(), m_settings.getUseIndex());
        m_table.updateFromSettings(m_settings);

        m_flowVars.setSelected(m_settings.publishConsumedCapUnits());
        m_consistentRead.setSelected(m_settings.isConsistentRead());

        m_hashKeyName.setText(m_settings.getHashKeyName());
        m_hashKeyValue.setText(m_settings.getHashKeyValue());
        m_hashKeyType.setSelectedItemValue(m_settings.getHashKeyType());

        m_rangeKeyCheckbox.setSelected(m_settings.isUseRangeKey());
        m_rangeKeyName.setText(m_settings.getRangeKeyName());
        m_rangeKeyValue1.setText(m_settings.getRangeKeyValue1());
        m_rangeKeyValue2.setText(m_settings.getRangeKeyValue2());
        m_rangeKeyType.setSelectedItemValue(m_settings.getRangeKeyType());
        m_rangeKeyOperator.setSelectedItem(m_settings.getRangeKeyOperator());

        m_fp.setFilterExpression(m_settings.getFilterExpr());
        m_fp.setProjectionExpression(m_settings.getProjectionExpr());

        m_fp.updatePlaceholdersFromSettings(m_settings.getPlaceholderSettings());

        m_table.setRegionOverwrite(m_conCredentials == null ? null : Region.of(m_conCredentials.getHost()));

        setBetweenVisible(m_settings.getRangeKeyOperator().equals("BETWEEN"));
        setRangeKeyEnabled(m_settings.isUseRangeKey());
        toggleFetchButton();
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_settings.setPublishConsumedCapUnits(m_flowVars.isSelected());

        m_table.saveToSettings(m_settings);

        m_settings.setScanIndexForward(m_scanForward.isSelected());
        m_settings.setLimit((int)m_limit.getValue());
        m_settings.setUseIndex(m_indexSelectionPanel.isUseIndex());
        m_settings.setIndexName(m_indexSelectionPanel.getIndexName());

        m_settings.setHashKeyName(m_hashKeyName.getText());
        m_settings.setHashKeyType(m_hashKeyType.getSelectedItemValue());
        m_settings.setHashKeyValue(m_hashKeyValue.getText());

        m_settings.setUseRangeKey(m_rangeKeyCheckbox.isSelected());
        m_settings.setRangeKeyName(m_rangeKeyName.getText());
        m_settings.setRangeKeyType(m_rangeKeyType.getSelectedItemValue());
        m_settings.setRangeKeyOperator((String) m_rangeKeyOperator.getSelectedItem());
        m_settings.setRangeKeyValue1(m_rangeKeyValue1.getText());
        m_settings.setRangeKeyValue2(m_rangeKeyValue2.getText());

        m_settings.setFilterExpr(m_fp.getFilterExpression());
        m_settings.setProjectionExpr(m_fp.getProjectionExpression());

        m_fp.savePlaceholdersToSettings(m_settings.getPlaceholderSettings());

        m_settings.setConsistentRead(m_consistentRead.isSelected());
        m_settings.saveSettings(settings);
    }
}