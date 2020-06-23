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
package org.knime.cloud.aws.dynamodb.utils;

import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.cloud.core.util.port.CloudConnectionInformationPortObjectSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.DataType;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;

/**
 * Helper methods for KNIME stuff, like creating table specs.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
public final class KNIMEUtil {
    
    private KNIMEUtil() { }
    
    /**
     * Creates a table spec from a map containing for each column a set of possible types.
     * @param types the types for each column name
     * @return a data table spec with a type for each column that matches all types for that column in the input map.
     */
    public static DataTableSpec createSpecFromTypes(final Map<String, Set<DataType>> types) {
        DataTableSpecCreator creator = new DataTableSpecCreator();
        for (Entry<String, Set<DataType>> column : types.entrySet()) {
            DataType type = column.getValue().stream().reduce((t1, t2) -> DataType.getCommonSuperType(t1, t2)).get();
            creator.addColumns(new DataColumnSpecCreator(column.getKey(), type).createSpec());
        }
        return creator.createSpec();
    }

	/**
	 * @param specs {@link PortObjectSpec}s where the first spec should be a
	 * {@link CloudConnectionInformationPortObjectSpec}
	 * @return the {@link CloudConnectionInformation}
	 * @throws NotConfigurableException if the spec does not contain a {@link CloudConnectionInformationPortObjectSpec}
	 */
	public static CloudConnectionInformation getConnectionInformationInDialog(final PortObjectSpec[] specs)
			throws NotConfigurableException {
		if (specs[0] instanceof CloudConnectionInformationPortObjectSpec) {
			return ((CloudConnectionInformationPortObjectSpec)specs[0]).getConnectionInformation();
		}
		throw new NotConfigurableException("No Amazon Credentials available");
	}
}
