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
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
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
 *   Aug 16, 2016 (budiyanto): created
 */
package org.knime.cloud.aws.util;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformation;
import org.knime.cloud.core.util.port.CloudConnectionInformation;

/**
 * The AWS connection information port view
 *
 * @author Budi Yanto, KNIME.com
 */
public class AWSConnectionInformationView extends JPanel {

    /**
     * Automatically generated serial version UUID
     */
    private static final long serialVersionUID = -4166582429664304717L;

    private static final String WHITE_SPACE = "&nbsp;&nbsp;";
    private static final String NEW_LINE = "<br/><br/>";

    /**
     * @param cloudConnectioInformation the {@link ConnectionInformation} containing the connection information
     */
    public AWSConnectionInformationView(final CloudConnectionInformation cloudConnectioInformation) {
        super(new GridBagLayout());
        super.setName("Connection");
        final StringBuilder buf = new StringBuilder("<html><body>");
        buf.append("<strong>Root URL:</strong>" + WHITE_SPACE);
        buf.append("<tt>" + cloudConnectioInformation.toString() + "</tt>");
        buf.append(NEW_LINE);

        buf.append("<strong>Authentication type:</strong>" + WHITE_SPACE);
        if(cloudConnectioInformation.useKeyChain()) {
            buf.append("<tt>" + "Default Credential Provider Chain"+ "</tt>");
        } else {
            buf.append("<tt>" + "Access Key ID & Secret Key" + "</tt>");
        }
        buf.append(NEW_LINE);

        if(!cloudConnectioInformation.useKeyChain()) {
            buf.append("<strong>Access key ID:</strong>" + WHITE_SPACE);
            buf.append("<tt>" + cloudConnectioInformation.getUser() + "</tt>");
            buf.append(NEW_LINE);
        }

        buf.append("<strong>Region:</strong>" + WHITE_SPACE);
        buf.append("<tt>" + cloudConnectioInformation.getHost() + "</tt>");
        buf.append(NEW_LINE);

        buf.append("<strong>Connection timeout in milliseconds:</strong>" + WHITE_SPACE);
        buf.append("<tt>" + cloudConnectioInformation.getTimeout() + "</tt>");

        final JTextPane textArea = new JTextPane();
        textArea.setContentType("text/html");
        textArea.setEditable(false);
        textArea.setText(buf.toString());
        textArea.setCaretPosition(0);
        final JScrollPane jsp = new JScrollPane(textArea);
        jsp.setPreferredSize(new Dimension(300, 300));
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        super.add(jsp, c);
    }

}
