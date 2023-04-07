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
 *   Oct 28, 2019 (Simon Schmid, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.cloud.aws.mlservices.nodes.personalize.upload;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.SchemaBuilder.FieldAssembler;
import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformation;
import org.knime.base.filehandling.remote.files.Connection;
import org.knime.base.filehandling.remote.files.ConnectionMonitor;
import org.knime.base.filehandling.remote.files.RemoteFile;
import org.knime.base.filehandling.remote.files.RemoteFileFactory;
import org.knime.base.node.io.csvwriter.CSVWriter;
import org.knime.base.node.io.csvwriter.FileWriterSettings;
import org.knime.cloud.aws.mlservices.nodes.personalize.AmazonPersonalizeConnection;
import org.knime.cloud.aws.mlservices.utils.personalize.AmazonPersonalizeUtils;
import org.knime.cloud.aws.mlservices.utils.personalize.AmazonPersonalizeUtils.Status;
import org.knime.cloud.aws.util.AmazonConnectionInformationPortObject;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.IntValue;
import org.knime.core.data.LongValue;
import org.knime.core.data.StringValue;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.container.ColumnRearranger;
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
import org.knime.core.util.FileUtil;

import software.amazon.awssdk.services.personalize.PersonalizeClient;
import software.amazon.awssdk.services.personalize.model.CreateDatasetGroupRequest;
import software.amazon.awssdk.services.personalize.model.CreateDatasetGroupResponse;
import software.amazon.awssdk.services.personalize.model.CreateDatasetImportJobRequest;
import software.amazon.awssdk.services.personalize.model.CreateDatasetRequest;
import software.amazon.awssdk.services.personalize.model.CreateSchemaRequest;
import software.amazon.awssdk.services.personalize.model.DataSource;
import software.amazon.awssdk.services.personalize.model.DatasetGroupSummary;
import software.amazon.awssdk.services.personalize.model.DatasetSchemaSummary;
import software.amazon.awssdk.services.personalize.model.DatasetSummary;
import software.amazon.awssdk.services.personalize.model.DeleteDatasetGroupRequest;
import software.amazon.awssdk.services.personalize.model.DeleteDatasetRequest;
import software.amazon.awssdk.services.personalize.model.DescribeDatasetGroupRequest;
import software.amazon.awssdk.services.personalize.model.DescribeDatasetGroupResponse;
import software.amazon.awssdk.services.personalize.model.DescribeDatasetImportJobRequest;
import software.amazon.awssdk.services.personalize.model.DescribeDatasetImportJobResponse;
import software.amazon.awssdk.services.personalize.model.InvalidInputException;
import software.amazon.awssdk.services.personalize.model.ListDatasetGroupsRequest;
import software.amazon.awssdk.services.personalize.model.ListDatasetGroupsResponse;
import software.amazon.awssdk.services.personalize.model.ListDatasetsRequest;
import software.amazon.awssdk.services.personalize.model.ListDatasetsResponse;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Abstract node model for Amazon Personalize data upload nodes.
 *
 * @author Simon Schmid, KNIME GmbH, Konstanz, Germany
 * @param <S> the object used to transfer settings from dialog to model
 */
public abstract class AbstractAmazonPersonalizeDataUploadNodeModel<S extends AbstractAmazonPersonalizeDataUploadNodeSettings>
    extends NodeModel {

    /** The index of the data table input port. */
    protected static final int TABLE_INPUT_PORT_IDX = 1;

    /** Max. number of characters for required fields. */
    protected static final int MAX_CHARACTERS_REQUIRED_FIELDS = 256;

    /** Max. number of characters for optional and metadata fields. */
    protected static final int MAX_CHARACTERS_METADATA_COLS = 1000;

    /** The Amazon identifier for item id. */
    protected static final String ITEM_ID = "ITEM_ID";

    /** The Amazon identifier for user id. */
    protected static final String USER_ID = "USER_ID";

    /** The Amazon identifier for event type. */
    protected static final String EVENT_TYPE = "EVENT_TYPE";

    private static final Map<String, String> MAP_COL_IDENTIFIER_LABEL;

    static {
        MAP_COL_IDENTIFIER_LABEL = new HashMap<>();
        MAP_COL_IDENTIFIER_LABEL.put(USER_ID, "user id");
        MAP_COL_IDENTIFIER_LABEL.put(ITEM_ID, "item id");
        MAP_COL_IDENTIFIER_LABEL.put(EVENT_TYPE, "event type");
    }

    private static final String SCHEMA_NAMESPACE = "com.amazonaws.personalize.schema";

    /** */
    protected static final String PREFIX_METADATA_FIELD = "METADATA_";

    private final String m_datasetType = getDatasetType();

    /** The settings */
    protected S m_settings;

    /**
     * @return the settings
     */
    protected abstract S getSettings();

    /** */
    protected AbstractAmazonPersonalizeDataUploadNodeModel() {
        super(new PortType[]{AmazonConnectionInformationPortObject.TYPE, BufferedDataTable.TYPE}, null);
    }

    /**
     * @return the type of the dataset that should be uploaded
     */
    protected abstract String getDatasetType();

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        if (m_settings == null) {
            throw new InvalidSettingsException("The node must be configured.");
        }
        // no output
        return null;
    }

    /**
     * @param namespace the namespace
     * @return the field assembler created with the proper properties
     */
    protected abstract FieldAssembler<Schema> createFieldAssembler(final String namespace);

    /**
     * @param spec the table spec of the already filtered and adapted table
     * @return a map containing column identifiers and corresponding character limits
     */
    protected abstract Map<String, Integer> getColumnCharLimitMap(final DataTableSpec spec);

    private void validateInputTableContent(final BufferedDataTable table) {
        final DataTableSpec spec = table.getSpec();
        final Map<String, Integer> requiredColLimitMap = getColumnCharLimitMap(spec);
        final Map<String, Integer> requiredColIdxMap =
            requiredColLimitMap.keySet().stream().collect(Collectors.toMap(e -> e, e -> spec.findColumnIndex(e)));
        final List<Integer> metaColIdxList = Arrays.stream(spec.getColumnNames())
            .filter(e -> e.startsWith(AbstractAmazonPersonalizeDataUploadNodeModel.PREFIX_METADATA_FIELD))
            .map(e -> spec.findColumnIndex(e)).collect(Collectors.toList());
        // iterate over the whole table and check content
        try (CloseableRowIterator iterator = table.iterator()) {
            while (iterator.hasNext()) {
                final DataRow row = iterator.next();
                // check length of required (and optional) columns
                for (final String colName : requiredColIdxMap.keySet()) {
                    final DataCell cell = row.getCell(requiredColIdxMap.get(colName));
                    if (!cell.isMissing()) {
                        final String stringValue = ((StringValue)cell).getStringValue();
                        final Integer limit = requiredColLimitMap.get(colName);
                        if (stringValue.length() > limit) {
                            throw new IllegalArgumentException("The " + MAP_COL_IDENTIFIER_LABEL.get(colName)
                                + " in row '" + row.getKey() + "' has too many characters. Maximum is " + limit + ".");
                        }
                    }
                }
                // check length of metadata columns
                for (final Integer idx : metaColIdxList) {
                    final DataCell cell = row.getCell(idx);
                    if (!cell.isMissing() && cell instanceof StringValue) {
                        final String stringValue = ((StringValue)cell).getStringValue();
                        if (stringValue.length() > MAX_CHARACTERS_METADATA_COLS) {
                            throw new IllegalArgumentException(
                                "One of the included metadata columns in row '" + row.getKey()
                                    + "' has too many characters. Maximum is " + MAX_CHARACTERS_METADATA_COLS + ".");
                        }
                    }
                }
            }
        }
    }

    /**
     * @return the prefix of the schema name
     */
    protected String getSchemaNamePrefix() {
        return m_settings.getPrefixSchemaName();
    }

    private String createSchema(final PersonalizeClient personalizeClient, final DataTableSpec spec) {
        final StringBuilder schemaNameBuilder = new StringBuilder(getSchemaNamePrefix());
        FieldAssembler<Schema> fieldAssembler = createFieldAssembler(SCHEMA_NAMESPACE);
        for (final String colName : spec.getColumnNames()) {
            if (!colName.startsWith(PREFIX_METADATA_FIELD)) {
                continue;
            }
            final DataColumnSpec colSpec = spec.getColumnSpec(colName);
            final boolean isCategorical;
            final Type type;
            if (colSpec.getType().isCompatible(StringValue.class)) {
                isCategorical = true;
                type = Type.STRING;
            } else if (colSpec.getType().isCompatible(IntValue.class)) {
                isCategorical = false;
                type = Type.INT;
            } else if (colSpec.getType().isCompatible(LongValue.class)) {
                isCategorical = false;
                type = Type.LONG;
            } else {
                isCategorical = false;
                type = Type.DOUBLE;
            }
            schemaNameBuilder.append("-" + type);
            // 'categorical' must be set for metadata
            fieldAssembler =
                fieldAssembler.name(colName).prop("categorical", isCategorical).type(Schema.create(type)).noDefault();
        }
        final String schemaName = schemaNameBuilder.toString();

        // check if the same schema has been created before
        final List<DatasetSchemaSummary> existingSchemas = AmazonPersonalizeUtils.listAllSchemas(personalizeClient);
        final Optional<DatasetSchemaSummary> schemaSummary =
            existingSchemas.stream().filter(e -> e.name().equals(schemaName)).findAny();
        // if so, use this one again
        if (schemaSummary.isPresent()) {
            return schemaSummary.get().schemaArn();
        }
        // otherwise create new one
        final Schema schema = fieldAssembler.endRecord();
        final CreateSchemaRequest createSchemaRequest =
            CreateSchemaRequest.builder().name(schemaName).schema(schema.toString()).build();
        return personalizeClient.createSchema(createSchemaRequest).schemaArn();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        // === Write table out as CSV ===  (TODO we may be able to write it directly to S3)
        // Filter included columns
        final BufferedDataTable filterTable = filterTable((BufferedDataTable)inObjects[TABLE_INPUT_PORT_IDX], exec);

        // Rename columns to fit the later created schema
        final BufferedDataTable adaptedTable = renameColumns(filterTable, exec);

        // Check if the input is valid (just a shallow check, there is no clear documentation by Amazon)
        exec.setMessage("Validating input");
        validateInputTableContent(adaptedTable);
        exec.setProgress(0.05);

        // Write the table as CSV to disc
        final URI sourceURI = writeCSV(adaptedTable, exec.createSubExecutionContext(0.1));

        // === Upload CSV to S3 ===
        final CloudConnectionInformation cxnInfo =
            ((AmazonConnectionInformationPortObject)inObjects[0]).getConnectionInformation();
        final String uniqueFilePath = m_settings.getTarget() + "KNIME-tmp-" + StringUtils.lowerCase(getDatasetType())
            + "-file-" + System.currentTimeMillis() + ".csv";
        final RemoteFile<Connection> target =
            writeToS3(exec.createSubExecutionContext(0.1), sourceURI, cxnInfo, uniqueFilePath);

        // === Import data from S3 to Amazon Personalize service ===
        try (final AmazonPersonalizeConnection personalizeConnection = new AmazonPersonalizeConnection(cxnInfo)) {
            final PersonalizeClient personalizeClient = personalizeConnection.getClient();

            // Create the dataset group ARN or use existing one
            final String datasetGroupArn = createDatasetGroup(personalizeClient, exec.createSubExecutionContext(0.2));

            // Check if the respective dataset already exists and either delete it or abort
            checkAlreadyExistingDataset(personalizeClient, datasetGroupArn, exec.createSubExecutionContext(0.1));
            exec.setProgress(0.5);

            // Create the data set (container)
            exec.setMessage("Importing dataset from S3");
            final String schemaArn = createSchema(personalizeClient, adaptedTable.getDataTableSpec());
            final String datasetArn = personalizeClient
                .createDataset(CreateDatasetRequest.builder().datasetGroupArn(datasetGroupArn)
                    .datasetType(m_datasetType).name(m_settings.getDatasetName()).schemaArn(schemaArn).build())
                .datasetArn();
            try {
                // Import the dataset from S3
                importDataFromS3(personalizeClient, "s3:/" + uniqueFilePath, datasetArn, exec);
            } catch (RuntimeException | InterruptedException e1) {
                try {
                    deleteDataset(personalizeClient, datasetGroupArn, datasetArn);
                } catch (InterruptedException e) {
                    // happens if user cancels node execution during deletion of dataset
                    // do nothing, deletion will be further processed by amazon
                }
                throw e1;
            }
        } catch (RuntimeException e) {
            // TODO cancel import job, currently not supported but hopefully in future versions
            throw e;
        } finally {
            // Remove temporary created S3 file
            target.delete();
        }

        return null;
    }

    private void importDataFromS3(final PersonalizeClient personalizeClient, final String s3FilePath,
        final String datasetArn, final ExecutionContext exec) throws InterruptedException {

        // Start the job that imports the dataset from S3
        final DataSource dataSource = DataSource.builder().dataLocation(s3FilePath).build();
        final String jobName = m_settings.getPrefixImportJobName() + "-" + System.currentTimeMillis();
        final String datasetImportJobArn;
        try {
            datasetImportJobArn = personalizeClient
                .createDatasetImportJob(CreateDatasetImportJobRequest.builder().datasetArn(datasetArn)
                    .roleArn(m_settings.getIamServiceRoleArn()).dataSource(dataSource).jobName(jobName).build())
                .datasetImportJobArn();

        } catch (InvalidInputException e) {
            throw new IllegalArgumentException(
                "The input is invalid. The reason could be too many missing values in one of the input columns. Error "
                    + "message from Amazon: " + e.awsErrorDetails().errorMessage(), e);
        }

        // Wait until status of dataset is ACTIVE
        final var describeDatasetImportJobRequest =
            DescribeDatasetImportJobRequest.builder().datasetImportJobArn(datasetImportJobArn).build();
        AmazonPersonalizeUtils.waitUntilActive(() -> {
            final DescribeDatasetImportJobResponse datasetImportJobDescription =
                personalizeClient.describeDatasetImportJob(describeDatasetImportJobRequest);
            final String status = datasetImportJobDescription.datasetImportJob().status();
            exec.setMessage("Importing dataset from S3 (Status: " + status + ")");
            if (status.equals(Status.CREATED_FAILED.getStatus())) {
                throw new IllegalStateException("No dataset has been created. Reason: "
                    + datasetImportJobDescription.datasetImportJob().failureReason());
            }
            return status.equals(Status.ACTIVE.getStatus());
        }, 2000);
    }

    private void checkAlreadyExistingDataset(final PersonalizeClient personalizeClient, final String datasetGroupArn,
        final ExecutionContext exec) throws InterruptedException {
        exec.setMessage("Checking already existing datasets");
        final ListDatasetsResponse listDatasets =
            personalizeClient.listDatasets(ListDatasetsRequest.builder().datasetGroupArn(datasetGroupArn).build());
        final Optional<DatasetSummary> dataset =
            listDatasets.datasets().stream().filter(e -> e.datasetType().equals(m_datasetType)).findFirst();
        if (dataset.isPresent()) {
            if (m_settings.getOverwriteDatasetPolicy().equals(OverwritePolicy.ABORT.toString())) {
                // Abort if dataset already exists
                throw new IllegalStateException("A dataset of type '" + getDatasetType()
                    + "' already exists. Either choose a different dataset group or select to overwrite the existing "
                    + "dataset.");
            } else {
                // Delete the existing dataset
                exec.setMessage("Deleting existing dataset");
                deleteDataset(personalizeClient, datasetGroupArn, dataset.get().datasetArn());
            }
        }
        exec.setProgress(1);
    }

    private void deleteDataset(final PersonalizeClient personalizeClient, final String datasetGroupArn,
        final String datasetARN) throws InterruptedException {
        personalizeClient.deleteDataset(DeleteDatasetRequest.builder().datasetArn(datasetARN).build());

        final ListDatasetsRequest listDatasetsRequest = ListDatasetsRequest.builder().datasetGroupArn(datasetGroupArn).build();
        AmazonPersonalizeUtils.waitUntilActive(() -> {
            final List<DatasetSummary> datasets = personalizeClient.listDatasets(listDatasetsRequest).datasets();
            return !datasets.stream().anyMatch(e -> e.datasetType().equals(m_datasetType));
        }, 500);
    }

    // Creates a new dataset group if not already existing
    private String createDatasetGroup(final PersonalizeClient personalizeClient, final ExecutionContext exec)
        throws InterruptedException {
        exec.setMessage("Creating dataset group");
        final ListDatasetGroupsRequest listDatasetGroupsRequest = ListDatasetGroupsRequest.builder().build();
        final ListDatasetGroupsResponse listDatasetGroups = personalizeClient.listDatasetGroups(listDatasetGroupsRequest);
        final String datasetGroupName = m_settings.getSelectedDatasetGroup();
        final String datasetGroupArn;
        final boolean existing =
            listDatasetGroups.datasetGroups().stream().anyMatch(e -> e.name().equals(datasetGroupName));
        if (!existing) {
            // Create new dataset group
            final CreateDatasetGroupResponse createDatasetGroup =
                personalizeClient.createDatasetGroup(CreateDatasetGroupRequest.builder().name(datasetGroupName).build());
            datasetGroupArn = createDatasetGroup.datasetGroupArn();
        } else {
            final Optional<DatasetGroupSummary> dataGroupSummary = listDatasetGroups.datasetGroups().stream()
                .filter(e -> e.name().equals(datasetGroupName)).findFirst();
            if (!dataGroupSummary.isPresent()) {
                // should never happen
                throw new IllegalStateException("Dataset group with name '" + datasetGroupName + "' not present.");
            }
            datasetGroupArn = dataGroupSummary.get().datasetGroupArn();
        }

        // Wait until dataset group is created and ACTIVE (even if the group already existed, make sure it's ACTIVE)
        final DescribeDatasetGroupRequest describeDatasetGroupRequest = DescribeDatasetGroupRequest
                .builder().datasetGroupArn(datasetGroupArn).build();
        AmazonPersonalizeUtils.waitUntilActive(() -> {
            final DescribeDatasetGroupResponse datasetGroupDescription =
                personalizeClient.describeDatasetGroup(describeDatasetGroupRequest);
            final String status = datasetGroupDescription.datasetGroup().status();
            exec.setMessage("Creating dataset group (Status: " + status + ")");
            if (status.equals(Status.CREATED_FAILED.getStatus())) {
                if (!existing) {
                    // Delete the dataset group that we tried to create
                    personalizeClient
                        .deleteDatasetGroup(DeleteDatasetGroupRequest.builder().datasetGroupArn(datasetGroupArn).build());
                    // Wait until the dataset group is deleted (should usually be very quick but you never know...)
                    try {
                        AmazonPersonalizeUtils.waitUntilActive(() -> {
                            return !personalizeClient.listDatasetGroups(listDatasetGroupsRequest).datasetGroups()
                                .stream().anyMatch(e -> e.name().equals(datasetGroupName));
                        }, 50);
                    } catch (InterruptedException e1) {
                        // unlikely case
                        // do nothing, the deletion will be further processed by amazon
                    }
                    throw new IllegalStateException("Dataset group creation failed. Reason: "
                        + datasetGroupDescription.datasetGroup().failureReason());
                }
                throw new IllegalStateException(
                    "The selected dataset group is in an invalid state: " + Status.CREATED_FAILED.getStatus()
                        + ". Reason: " + datasetGroupDescription.datasetGroup().failureReason());
            }
            return status.equals(Status.ACTIVE.getStatus());
        }, 500);
        exec.setProgress(1);
        return datasetGroupArn;
    }

    // keep only included columns (required and metadata)
    private BufferedDataTable filterTable(final BufferedDataTable table, final ExecutionContext exec)
        throws CanceledExecutionException {
        final String[] includes = m_settings.getFilterConfig().applyTo(table.getDataTableSpec()).getIncludes();
        final ColumnRearranger columnRearranger = new ColumnRearranger(table.getDataTableSpec());
        columnRearranger.keepOnly(includes);
        final BufferedDataTable filteredTable = exec.createColumnRearrangeTable(table, columnRearranger, exec);
        return filteredTable;
    }

    /**
     * Returns a table that fulfills the naming requirements of Amazon Personalize for different dataset types.
     *
     * @param table input table
     * @param exec execution context
     * @return the input table with properly renamed columns
     * @throws CanceledExecutionException if the execution is canceled
     */
    protected abstract BufferedDataTable renameColumns(final BufferedDataTable table, final ExecutionContext exec)
        throws CanceledExecutionException;

    private static RemoteFile<Connection> writeToS3(final ExecutionContext exec, final URI sourceURI,
        final ConnectionInformation connectionInformation, final String filePath) throws URISyntaxException, Exception {
        final URI folderUri = new URI(connectionInformation.toURI().toString() + "/" + filePath);
        final ConnectionMonitor<Connection> monitor = new ConnectionMonitor<>();
        final RemoteFile<Connection> target =
            RemoteFileFactory.createRemoteFile(folderUri, connectionInformation, monitor);
        final RemoteFile<Connection> sourceFile = RemoteFileFactory.createRemoteFile(sourceURI, null, null);
        target.write(sourceFile, exec);
        return target;
    }

    @SuppressWarnings("resource")
    private static URI writeCSV(final BufferedDataTable table, final ExecutionContext exec)
        throws IOException, CanceledExecutionException {
        final File createTempDir = FileUtil.createTempFile("KNIME_amazon_personalize_", ".csv");
        final BufferedOutputStream tempOut = new BufferedOutputStream(new FileOutputStream(createTempDir));
        final FileWriterSettings settings = new FileWriterSettings();
        settings.setWriteColumnHeader(true);
        try (CSVWriter tableWriter = new CSVWriter(new OutputStreamWriter(tempOut, "UTF-8"), settings)) {
            tableWriter.write(table, exec);
        }
        return createTempDir.toURI();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        if (m_settings != null) {
            m_settings.saveSettings(settings);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        if (m_settings == null) {
            m_settings = getSettings();
        }
        m_settings.loadSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // nothing to do
    }

}
