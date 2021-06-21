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
package org.knime.cloud.aws.dynamodb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.DataContainer;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.node.BufferedDataTable;

/**
 * {@link DataContainer}-like class that dynamically handles incoming rows with different columns and column types.
 * The container has an in-memory buffer that it fills with incoming rows. Once the buffer is full,
 * the columns written into the buffer are checked against the current {@link DataTableSpec} of the
 * previously written data. If new columns or existing columns with new data types were written,
 * the DataTableSpec is adjusted accordingly and the data written into a new table with the updated DataTableSpec.
 * Use sparingly, as every time the DataTableSpec has to change, all previously written data has to be copied.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
public class DynamicDataContainer {

    private static final int DEFAULT_BUFFER_SIZE = 100;
    
    private int m_bufferSize;
    private DataContainer m_current;
    private List<Map<String, DataCell>> m_buffer;
    private List<RowKey> m_bufferedRowKeys;
    private Map<String, DataType> m_bufferedCols;
    private Function<DataTableSpec, DataContainer> m_dcSupplier;
    private boolean m_closed = false;
    
    /**
     * Creates a new {@code DynamicDataContainer} instance with the default buffer size of 100.
     * @param dataContainerSupplier the supplier function for a normal data container used by this container
     */
    public DynamicDataContainer(final Function<DataTableSpec, DataContainer> dataContainerSupplier) {
        this(dataContainerSupplier, DEFAULT_BUFFER_SIZE);
    }
    
    /**
     * Creates a new {@code DynamicDataContainer} instance.
     * @param dataContainerSupplier the supplier function for a normal data container used by this container
     * @param bufferSize the number of rows to keep in memory before creating
     * a new table spec and writing to a normal {@link DataContainer}
     */
    public DynamicDataContainer(final Function<DataTableSpec, DataContainer> dataContainerSupplier,
            final int bufferSize) {
        m_dcSupplier = dataContainerSupplier;
        m_bufferSize = bufferSize;
        m_buffer = new ArrayList<>(m_bufferSize);
        m_bufferedRowKeys = new ArrayList<>(m_bufferSize);
        m_bufferedCols = new LinkedHashMap<>();
    }
    
    /**
     * Adds a row to the container.
     * @param key the key of the new row
     * @param cells the cells of the new row
     * @param colNames the column names corresponding to the cells
     */
    public void addRow(final RowKey key, final DataCell[] cells, final String[] colNames) {
        if (m_closed) {
            throw new IllegalStateException("The container is closed");
        }
        if (cells.length != colNames.length) {
            throw new IllegalArgumentException("Cells and column names arrays must have the same length");
        }
        // Convert to map for storage in buffer
        HashMap<String, DataCell> map = new HashMap<>();
        for (int i = 0; i < cells.length; i++) {
            map.put(colNames[i], cells[i]);
            // If column exists in buffer, merge types, otherwise just insert
            m_bufferedCols.merge(colNames[i], cells[i].getType(), (t1, t2) -> DataType.getCommonSuperType(t1, t2));
        }
        m_bufferedRowKeys.add(key);
        m_buffer.add(map);
        if (m_buffer.size() == m_bufferSize) {
            flushBuffer();
        }
    }
    
    /**
     * Adds a row to the container.
     * @param key the key of the new row
     * @param data the mapping from column name to cell for the new row
     */
    public void addRow(final RowKey key, final Map<String, DataCell> data) {
        if (m_closed) {
            throw new IllegalStateException("The container is closed");
        }
        m_buffer.add(data);
        m_bufferedRowKeys.add(key);
        for (Entry<String, DataCell> e : data.entrySet()) {
            m_bufferedCols.merge(e.getKey(), e.getValue().getType(),
                    (t1, t2) -> DataType.getCommonSuperType(t1, t2));
        }
        if (m_buffer.size() == m_bufferSize) {
            flushBuffer();
        }
    }
    
    /**
     * Closes the container so that no more rows can be added.
     */
    public void close() {
        flushBuffer();
        if (m_current != null) {
            m_current.close();
        }
        m_closed = true;
    }
    
    /**
     * Creates the table from all the rows added to the container.
     * @return a {@link BufferedDataTable} with the rows previously added to the container
     * or an empty table if no rows where added.
     */
    public BufferedDataTable getTable() {
        if (!m_closed) {
            throw new IllegalStateException("The container must be closed before accessing the table");
        }
        if (m_current == null) {
            // Nothing was ever added, return empty table without any columns
            DataContainer dc = m_dcSupplier.apply(new DataTableSpec());
            dc.close();
            return (BufferedDataTable)dc.getTable();
        } else {
            return (BufferedDataTable)m_current.getTable();
        }
    }
    
    private DataTableSpec createSpecFromBuffer() {
        DataTableSpecCreator specCreator = new DataTableSpecCreator();
        for (Entry<String, DataType> col : m_bufferedCols.entrySet()) {
            specCreator.addColumns(new DataColumnSpecCreator(col.getKey(), col.getValue()).createSpec());
        }
        return specCreator.createSpec();
    }

    private DataRow mapToRow(final RowKey rk, final Map<String, DataCell> row) {
        DataTableSpec spec = m_current.getTableSpec();
        DataCell[] cells = new DataCell[spec.getNumColumns()];
        for (int c = 0; c < cells.length; c++) {
            String colName = spec.getColumnNames()[c];
            DataCell cell = row.get(colName);
            cells[c] = cell == null ? DataType.getMissingCell() : cell;
        }
        return new DefaultRow(rk, cells);
    }
    
    private void writeRowsFromBuffer() {
        for (int i = 0; i < m_buffer.size(); i++) {
            m_current.addRowToTable(mapToRow(m_bufferedRowKeys.get(i), m_buffer.get(i)));
        }
    }

    private DataTableSpec extendSpec(final DataTableSpec spec,
            final Map<String, DataType> newCols, final Map<String, DataType> changedCols) {
        DataTableSpecCreator specCreator = new DataTableSpecCreator();
        for (int c = 0; c < spec.getNumColumns(); c++) {
            DataColumnSpec cSpec = spec.getColumnSpec(c);
            DataType extType = changedCols.get(cSpec.getName());
            if (extType == null) {
                // Nothing changed, add column as is
                specCreator.addColumns(cSpec);
            } else {
                // New type, adjust spec accordingly
                DataType newType = DataType.getCommonSuperType(cSpec.getType(), extType);
                specCreator.addColumns(new DataColumnSpecCreator(cSpec.getName(), newType).createSpec());
            }
        }
        // Add new columns that have not been in the table before
        for (Entry<String, DataType> nc : newCols.entrySet()) {
            specCreator.addColumns(new DataColumnSpecCreator(nc.getKey(), nc.getValue()).createSpec());
        }
        return specCreator.createSpec();
    }
    
    private void flushBuffer() {
        if (m_buffer.isEmpty()) {
            return;
        }
        if (m_current == null) {
            m_current = m_dcSupplier.apply(createSpecFromBuffer());
            writeRowsFromBuffer();
        } else {
            // Find new columns and columns with same name but different type
            DataTableSpec spec = m_current.getTableSpec();
            Map<String, DataType> newCols = new LinkedHashMap<>();
            Map<String, DataType> changedCols = new HashMap<>();
            for (Entry<String, DataType> col : m_bufferedCols.entrySet()) {
                DataColumnSpec currentCol = spec.getColumnSpec(col.getKey());
                if (currentCol == null) {
                    newCols.put(col.getKey(), col.getValue());
                } else {
                    DataType colType = currentCol.getType();
                    if (!colType.isASuperTypeOf(col.getValue())) {
                        changedCols.put(col.getKey(), DataType.getCommonSuperType(colType, col.getValue()));
                    }
                }
            }
            if (newCols.isEmpty() && changedCols.isEmpty()) {
                // No new columns or types, we can just add all the data cells
                writeRowsFromBuffer();
            } else {
                // New columns or column types, merge specs and copy old table into new DataContainer
                m_current.close();
                DataTable old = m_current.getTable();
                DataTableSpec oldSpec = old.getDataTableSpec();
                
                // New columns or types --> create new data container and copy old rows
                DataTableSpec newSpec = extendSpec(spec, newCols, changedCols);
                m_current = m_dcSupplier.apply(newSpec);
                // Copy old rows and fill new columns with missing values
                for (DataRow row : old) {
                    DataCell[] cells = new DataCell[newSpec.getNumColumns()];
                    for (int c = 0; c < newSpec.getNumColumns(); c++) {
                        int idx = oldSpec.findColumnIndex(newSpec.getColumnSpec(c).getName());
                        if (idx == -1) {
                            cells[c] = DataType.getMissingCell();
                        } else {
                            cells[c] = row.getCell(idx);
                        }
                    }
                    m_current.addRowToTable(new DefaultRow(row.getKey(), cells));
                }
                // Now add new rows from buffer
                writeRowsFromBuffer();
            }
        }
        m_buffer.clear();
        m_bufferedCols.clear();
        m_bufferedRowKeys.clear();
    }
}
