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
package org.knime.cloud.aws.dynamodb.describetable;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.knime.cloud.aws.dynamodb.utils.DynamoDBUtil;
import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.collection.CollectionCellFactory;
import org.knime.core.data.collection.SetCell;
import org.knime.core.data.container.DataContainer;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCellFactory;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * The {@code NodeModel} for the DynamoDB Describe Table node.
 *
 * @author Alexander Fillbrunn, University of Konstanz
 */
final class DynamoDBDescribeTableNodeModel extends NodeModel {

    private final DynamoDBDescribeTableSettings m_settings = new DynamoDBDescribeTableSettings();

    /**
     * Default Constructor.
     */
    DynamoDBDescribeTableNodeModel() {
        super(new PortType[] {AmazonConnectionInformationPortObject.TYPE},
                new PortType[] {AmazonConnectionInformationPortObject.TYPE, BufferedDataTable.TYPE,
                		BufferedDataTable.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[] {inSpecs[0], createTableInfoSpec(), createIndexInfoSpec()};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        final CloudConnectionInformation con = inObjects[0] == null
                ? null : ((AmazonConnectionInformationPortObject)inObjects[0]).getConnectionInformation();

        final DynamoDbClient ddb = DynamoDBUtil.createClient(m_settings, con);

        final DataContainer tableInfoContainer = exec.createDataContainer(createTableInfoSpec());
        final DataContainer indexInfoContainer = exec.createDataContainer(createIndexInfoSpec());

        TableDescription table;
        try {
            table = ddb.describeTable(
                DescribeTableRequest.builder().tableName(m_settings.getTableName()).build()).table();
        } catch (final ResourceNotFoundException e) {
            throw new InvalidSettingsException(
                    String.format("The given table \"%s\" does not exist.", m_settings.getTableName()), e);
        }

        final Map<String, String> typeMapping = new HashMap<>();
        for (final AttributeDefinition def : table.attributeDefinitions()) {
            typeMapping.put(def.attributeName(), def.attributeTypeAsString());
        }

        // General table info
        final DataCell tableName = new StringCell(table.tableName());
        final DataCell id = new StringCell(table.tableId());
        final DataCell arn = new StringCell(table.tableArn());
        final DataCell status = new StringCell(table.tableStatusAsString());
        final DataCell sizeBytes = new LongCell(table.tableSizeBytes());
        final DataCell itemCount = new LongCell(table.itemCount());
        final DataCell creationDateTime = ZonedDateTimeCellFactory.create(
                ZonedDateTime.ofInstant(table.creationDateTime(), ZoneId.systemDefault()));

        // Provisioned throughput
        final DataCell readUnits = new LongCell(table.provisionedThroughput().readCapacityUnits());
        final DataCell writeUnits = new LongCell(table.provisionedThroughput().writeCapacityUnits());
        final DataCell lastDecrease = table.provisionedThroughput().lastDecreaseDateTime() == null ? DataType.getMissingCell()
                : ZonedDateTimeCellFactory.create(ZonedDateTime.ofInstant(
                        table.provisionedThroughput().lastDecreaseDateTime(), ZoneId.systemDefault()));
        final DataCell lastIncrease = table.provisionedThroughput().lastIncreaseDateTime() == null ? DataType.getMissingCell()
                : ZonedDateTimeCellFactory.create(ZonedDateTime.ofInstant(
                        table.provisionedThroughput().lastIncreaseDateTime(), ZoneId.systemDefault()));
        final DataCell nDecreasesToday = new LongCell(table.provisionedThroughput().numberOfDecreasesToday());

        // Billing
        final DataCell billingMode = table.billingModeSummary() == null
                ? DataType.getMissingCell() : new StringCell(table.billingModeSummary().billingModeAsString());

        // Keys
        DataCell hashKeyName = null;
        DataCell hashKeyType = null;
        DataCell rangeKeyName = DataType.getMissingCell();
        DataCell rangeKeyType = DataType.getMissingCell();
        for (final KeySchemaElement e : table.keySchema()) {
            if (e.keyType() == KeyType.HASH) {
                hashKeyName = new StringCell(e.attributeName());
                hashKeyType = new StringCell(typeMapping.get(e.attributeName()));
            } else {
                rangeKeyName = new StringCell(e.attributeName());
                rangeKeyType = new StringCell(typeMapping.get(e.attributeName()));
            }
        }

        tableInfoContainer.addRowToTable(new DefaultRow(new RowKey("Table Info"),
                tableName, id, arn, status, sizeBytes, itemCount, creationDateTime,
                readUnits, writeUnits, lastDecrease, lastIncrease, nDecreasesToday, billingMode,
                hashKeyName, hashKeyType, rangeKeyName, rangeKeyType));

        tableInfoContainer.close();

        for (final GlobalSecondaryIndexDescription gsid : table.globalSecondaryIndexes()) {
            indexInfoContainer.addRowToTable(rowFromGlobalIndex(gsid, typeMapping));
        }
        for (final LocalSecondaryIndexDescription lsid : table.localSecondaryIndexes()) {
            indexInfoContainer.addRowToTable(rowFromLocalIndex(lsid, typeMapping));
        }

        indexInfoContainer.close();

        return new PortObject[] {inObjects[0], (BufferedDataTable)tableInfoContainer.getTable(),
                                 (BufferedDataTable)indexInfoContainer.getTable()};
    }

    private DataRow rowFromLocalIndex(final LocalSecondaryIndexDescription lsid,
            final Map<String, String> typeMapping) {
        final DataCell indexType = new StringCell("local");
        final DataCell indexName = new StringCell(lsid.indexName());
        final DataCell indexArn = new StringCell(lsid.indexArn());
        final DataCell indexStatus = DataType.getMissingCell();
        final DataCell indexSizeBytes = new LongCell(lsid.indexSizeBytes());
        final DataCell indexItemCount = new LongCell(lsid.itemCount());
        final DataCell backfilling = DataType.getMissingCell();

        // Provisioned throughput
        final DataCell indexReadUnits = DataType.getMissingCell();
        final DataCell indexWriteUnits = DataType.getMissingCell();
        final DataCell indexLastDecrease = DataType.getMissingCell();
        final DataCell indexLastIncrease = DataType.getMissingCell();
        final DataCell indexNDecreasesToday = DataType.getMissingCell();

        // Keys
        DataCell indexHashKeyName = null;
        DataCell indexHashKeyType = null;
        DataCell indexRangeKeyName = DataType.getMissingCell();
        DataCell indexRangeKeyType = DataType.getMissingCell();
        for (final KeySchemaElement e : lsid.keySchema()) {
            if (e.keyType() == KeyType.HASH) {
                indexHashKeyName = new StringCell(e.attributeName());
                indexHashKeyType = new StringCell(typeMapping.get(e.attributeName()));
            } else {
                indexRangeKeyName = new StringCell(e.attributeName());
                indexRangeKeyType = new StringCell(typeMapping.get(e.attributeName()));
            }
        }

        final DataCell projectionType = new StringCell(lsid.projection().projectionTypeAsString());
        DataCell projection = DataType.getMissingCell();
        if (lsid.projection().projectionType() == ProjectionType.INCLUDE) {
            projection = CollectionCellFactory.createSetCell(
                    lsid.projection().nonKeyAttributes().stream()
                    .map(StringCell::new).collect(Collectors.toList()));
        }

        return new DefaultRow(new RowKey(lsid.indexName()),
                indexType, indexName, indexArn, indexStatus, indexSizeBytes, indexItemCount, backfilling,
                indexReadUnits, indexWriteUnits, indexLastDecrease, indexLastIncrease, indexNDecreasesToday,
                indexHashKeyName, indexHashKeyType, indexRangeKeyName, indexRangeKeyType,
                projectionType, projection);
    }

    private DataRow rowFromGlobalIndex(final GlobalSecondaryIndexDescription gsid,
            final Map<String, String> typeMapping) {
        final DataCell indexType = new StringCell("global");
        final DataCell indexName = new StringCell(gsid.indexName());
        final DataCell indexArn = new StringCell(gsid.indexArn());
        final DataCell indexStatus = new StringCell(gsid.indexStatusAsString());
        final DataCell indexSizeBytes = new LongCell(gsid.indexSizeBytes());
        final DataCell indexItemCount = new LongCell(gsid.itemCount());
        final DataCell backfilling = (gsid.backfilling() == null || !gsid.backfilling())
                ? BooleanCell.FALSE : BooleanCell.TRUE;

        // Provisioned throughput
        final DataCell indexReadUnits = new LongCell(gsid.provisionedThroughput().readCapacityUnits());
        final DataCell indexWriteUnits = new LongCell(gsid.provisionedThroughput().writeCapacityUnits());
        final DataCell indexLastDecrease = gsid.provisionedThroughput().lastDecreaseDateTime() == null
                ? DataType.getMissingCell() : ZonedDateTimeCellFactory.create(ZonedDateTime.ofInstant(
                        gsid.provisionedThroughput().lastDecreaseDateTime(), ZoneId.systemDefault()));
        final DataCell indexLastIncrease = gsid.provisionedThroughput().lastIncreaseDateTime() == null
                ? DataType.getMissingCell() : ZonedDateTimeCellFactory.create(ZonedDateTime.ofInstant(
                        gsid.provisionedThroughput().lastIncreaseDateTime(), ZoneId.systemDefault()));
        final DataCell indexNDecreasesToday = new LongCell(gsid.provisionedThroughput().numberOfDecreasesToday());

        // Keys
        DataCell indexHashKeyName = null;
        DataCell indexHashKeyType = null;
        DataCell indexRangeKeyName = DataType.getMissingCell();
        DataCell indexRangeKeyType = DataType.getMissingCell();
        for (final KeySchemaElement e : gsid.keySchema()) {
            if (e.keyType() == KeyType.HASH) {
                indexHashKeyName = new StringCell(e.attributeName());
                indexHashKeyType = new StringCell(typeMapping.get(e.attributeName()));
            } else {
                indexRangeKeyName = new StringCell(e.attributeName());
                indexRangeKeyType = new StringCell(typeMapping.get(e.attributeName()));
            }
        }

        final DataCell projectionType = new StringCell(gsid.projection().projectionTypeAsString());
        DataCell projection = DataType.getMissingCell();
        if (gsid.projection().projectionType() == ProjectionType.INCLUDE) {
            projection = CollectionCellFactory.createSetCell(
                    gsid.projection().nonKeyAttributes().stream()
                    .map(StringCell::new).collect(Collectors.toList()));
        }
        return new DefaultRow(new RowKey(gsid.indexName()),
                indexType, indexName, indexArn, indexStatus, indexSizeBytes, indexItemCount, backfilling,
                indexReadUnits, indexWriteUnits, indexLastDecrease, indexLastIncrease, indexNDecreasesToday,
                indexHashKeyName, indexHashKeyType, indexRangeKeyName, indexRangeKeyType,
                projectionType, projection);
    }

    private DataTableSpec createIndexInfoSpec() {
        final DataTableSpecCreator c = new DataTableSpecCreator();
        c.addColumns(
                new DataColumnSpecCreator("type", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("name", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("arn", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("status", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("sizeInBytes", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("itemCount", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("backfilling", BooleanCell.TYPE).createSpec(),
                new DataColumnSpecCreator("readUnits", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("writeUnits", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("lastProvThroughputDecrease", ZonedDateTimeCellFactory.TYPE).createSpec(),
                new DataColumnSpecCreator("lastProvThroughputIncrease", ZonedDateTimeCellFactory.TYPE).createSpec(),
                new DataColumnSpecCreator("provThroughputDecreasesToday", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("hashKeyName", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("hashKeyType", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("rangeKeyName", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("rangeKeyType", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("projectionType", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("nonKeyAttributes", SetCell.getCollectionType(StringCell.TYPE)).createSpec()
                );
        return c.createSpec();
    }

    private DataTableSpec createTableInfoSpec() {
        final DataTableSpecCreator c = new DataTableSpecCreator();
        c.addColumns(
                new DataColumnSpecCreator("name", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("id", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("arn", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("status", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("sizeInBytes", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("itemCount", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("creationDateTime", ZonedDateTimeCellFactory.TYPE).createSpec(),
                new DataColumnSpecCreator("readUnits", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("writeUnits", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("lastProvThroughputDecrease", ZonedDateTimeCellFactory.TYPE).createSpec(),
                new DataColumnSpecCreator("lastProvThroughputIncrease", ZonedDateTimeCellFactory.TYPE).createSpec(),
                new DataColumnSpecCreator("provThroughputDecreasesToday", LongCell.TYPE).createSpec(),
                new DataColumnSpecCreator("billingMode", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("hashKeyName", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("hashKeyType", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("rangeKeyName", StringCell.TYPE).createSpec(),
                new DataColumnSpecCreator("rangeKeyType", StringCell.TYPE).createSpec()
                );
        return c.createSpec();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        final DynamoDBDescribeTableSettings s = new DynamoDBDescribeTableSettings();
        s.loadSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.loadSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
    }
}
