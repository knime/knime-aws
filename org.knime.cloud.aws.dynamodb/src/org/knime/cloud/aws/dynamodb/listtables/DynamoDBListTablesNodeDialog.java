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
package org.knime.cloud.aws.dynamodb.listtables;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;

import software.amazon.awssdk.regions.Region;

/**
 * Dialog for the DynamoDB List Tables node.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
final class DynamoDBListTablesNodeDialog extends NodeDialogPane {

    private final DynamoDBListTablesSettings m_settings = new DynamoDBListTablesSettings();

    private final JComboBox<Region> m_region = new JComboBox<>(Region.regions().toArray(new Region[0]));
    private final JTextField m_endpoint = new JTextField(10);
    private final JSpinner m_limit = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 10));

    /**
     * Creates a new instance of the dialog.
     */
    DynamoDBListTablesNodeDialog() {
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

        stdSettings.add(createDatabaseTab(), c);

        return stdSettings;
    }

    private JPanel createDatabaseTab() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setLayout(new GridBagLayout());

        final GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        panel.add(new JLabel("Region"), c);

        c.gridx++;
        panel.add(m_region, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("Custom Endpoint"), c);

        c.gridx++;
        panel.add(m_endpoint, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("Limit (0 = all)"), c);

        c.gridx++;
        panel.add(m_limit, c);

        panel.setBorder(BorderFactory.createTitledBorder("Database"));
        return panel;
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_settings.loadSettingsForDialog(settings);
        m_region.setSelectedItem(m_settings.getRegion());
        m_endpoint.setText(m_settings.getEndpoint());
        m_limit.setValue(m_settings.getLimit());
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_settings.setRegion((Region)m_region.getSelectedItem());
        m_settings.setEndpoint(m_endpoint.getText());
        m_settings.setLimit((int)m_limit.getValue());
        m_settings.saveSettings(settings);
    }
}
