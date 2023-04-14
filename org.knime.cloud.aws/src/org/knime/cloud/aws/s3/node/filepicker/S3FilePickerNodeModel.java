package org.knime.cloud.aws.s3.node.filepicker;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.knime.base.filehandling.NodeUtils;
import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformation;
import org.knime.base.filehandling.remote.files.Connection;
import org.knime.base.filehandling.remote.files.ConnectionMonitor;
import org.knime.base.filehandling.remote.files.RemoteFileFactory;
import org.knime.cloud.aws.s3.filehandler.S3RemoteFile;
import org.knime.cloud.core.node.filepicker.AbstractFilePickerNodeModel;
import org.knime.core.node.NodeLogger;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * This is the model implementation of S3ConnectionToUrl.
 *
 *
 * @author Budi Yanto, KNIME.com
 */
public class S3FilePickerNodeModel extends AbstractFilePickerNodeModel{

	private static NodeLogger LOGGER = NodeLogger.getLogger(S3FilePickerNodeModel.class);

	static final String CFG_FILE_SELECTION = "file-selection";

	/* The name of the output flow variable */
	private static final String FLOW_VARIABLE_NAME = "S3PickedFile";

	/**
	 * Constructor for the node model.
	 */
	protected S3FilePickerNodeModel() {
		super(CFG_FILE_SELECTION, FLOW_VARIABLE_NAME);
	}

	/**
	 * {@inheritDoc}
	 * @throws Exception
	 */
	@SuppressWarnings("resource")
    @Override
	protected String getSignedURL(final ConnectionMonitor<? extends Connection> monitor, final ConnectionInformation connectionInformation) throws Exception {
		final URI uri = new URI(connectionInformation.toURI().toString() + NodeUtils.encodePath(getFileSelection()));
		final S3RemoteFile remoteFile = (S3RemoteFile) RemoteFileFactory.createRemoteFile(uri,
				connectionInformation, monitor);

		remoteFile.open();
		final var s3Presigner = remoteFile.getConnection().getPresigner();
		final String bucketName = remoteFile.getContainerName();
		final String key = remoteFile.getBlobName();

		final Date expirationTime = getExpirationTime();

		LOGGER.debug("Generate Presigned URL with expiration time: " + expirationTime);

		final var getObjectRequest = GetObjectRequest.builder()
		        .bucket(bucketName).key(key).build();

        final var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.between(Instant.now(), expirationTime.toInstant()))
                .getObjectRequest(getObjectRequest).build();

        final var presignedRequest = s3Presigner.presignGetObject(presignRequest);
		return presignedRequest.url().toURI().toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getEndpointPrefix() {
		return S3Client.SERVICE_METADATA_ID;
	}

}
