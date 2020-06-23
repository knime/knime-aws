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
package org.knime.cloud.aws.dynamodb.scan;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.knime.cloud.aws.dynamodb.settings.DynamoDBTableSettings;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBFilterAndProjectPanel;
import org.knime.cloud.aws.dynamodb.ui.DynamoDBTablePanel;
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
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * Dialog for the DynamoDB Scan node.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
final class DynamoDBScanNodeDialog extends NodeDialogPane {

    private CloudConnectionInformation m_conCredentials = null;

    private final DynamoDBScanSettings m_settings = new DynamoDBScanSettings();
    private DynamoDBTablePanel m_table;
    private final DynamoDBFilterAndProjectPanel m_fp = new DynamoDBFilterAndProjectPanel();

    // Scan
    private final JCheckBox m_consistentRead = new JCheckBox("Consistent Read");
    private final JSpinner m_limit = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    private final JCheckBox m_flowVars = new JCheckBox("Publish consumed capacity units as flow variable");

    private IndexSelectionPanel m_indexSelectionPanel;

    /**
     * Creates a new instance of the dialog.
     */
    public DynamoDBScanNodeDialog() {
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

        m_table = new DynamoDBTablePanel(createFlowVariableModel(
                DynamoDBTableSettings.CFG_TABLE_NAME, Type.STRING), this::getTableNames);
        stdSettings.add(m_table, c);

        c.gridy++;
        stdSettings.add(createScanPanel(), c);

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

    private JPanel createScanPanel() {
        final JPanel scanPanel = new JPanel(new GridBagLayout());
        final GridBagConstraints kc = new GridBagConstraints();
        kc.insets = new Insets(2, 2, 2, 2);
        kc.gridx = 0;
        kc.gridy = 0;
        kc.weightx = 1;
        kc.fill = GridBagConstraints.HORIZONTAL;

        kc.gridwidth = 2;
        m_indexSelectionPanel = new IndexSelectionPanel(this::getIndexNames);
        scanPanel.add(m_indexSelectionPanel, kc);

        kc.anchor = GridBagConstraints.WEST;
        kc.fill = GridBagConstraints.NONE;
        kc.gridwidth = 1;
        kc.gridx = 0;
        kc.gridy++;
        kc.weightx = 1;
        scanPanel.add(new JLabel("Limit (0 = all)"), kc);

        kc.gridx++;
        kc.weightx = 0;
        scanPanel.add(m_limit, kc);

        kc.gridx = 0;
        kc.gridy++;
        scanPanel.add(m_consistentRead, kc);

        return scanPanel;
    }

    private List<String> getTableNames() {
        try {
            return DynamoDBUtil.getTableNames(m_conCredentials, 20);
        } catch (final Exception e1) {
            return null;
        }
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

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_settings.loadSettingsForDialog(settings);

        m_limit.setValue(m_settings.getLimit());
        m_indexSelectionPanel.update(m_settings.getIndexName(), m_settings.getUseIndex());
        m_table.updateFromSettings(m_settings);
        m_flowVars.setSelected(m_settings.publishConsumedCapUnits());
        m_fp.setFilterExpression(m_settings.getFilterExpr());
        m_fp.setProjectionExpression(m_settings.getProjectionExpr());

        m_fp.updatePlaceholdersFromSettings(m_settings.getPlaceholderSettings());

        m_consistentRead.setSelected(m_settings.isConsistentRead());

        m_conCredentials = KNIMEUtil.getConnectionInformationInDialog(specs);

        m_table.setRegionOverwrite(m_conCredentials == null ? null : Region.of(m_conCredentials.getHost()));
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {

        m_settings.setPublishConsumedCapUnits(m_flowVars.isSelected());

        m_table.saveToSettings(m_settings);

        m_settings.setUseIndex(m_indexSelectionPanel.isUseIndex());
        m_settings.setLimit((int)m_limit.getValue());
        m_settings.setIndexName(m_indexSelectionPanel.getIndexName());
        m_settings.setFilterExpr(m_fp.getFilterExpression());
        m_settings.setProjectionExpr(m_fp.getProjectionExpression());

        m_fp.savePlaceholdersToSettings(m_settings.getPlaceholderSettings());
        m_settings.setConsistentRead(m_consistentRead.isSelected());
        m_settings.saveSettings(settings);
    }
}
