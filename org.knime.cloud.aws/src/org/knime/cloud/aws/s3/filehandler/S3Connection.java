package org.knime.cloud.aws.s3.filehandler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.knime.base.filehandling.remote.files.Connection;
import org.knime.cloud.aws.sdkv2.util.AWSCredentialHelper;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * S3 Connection
 *
 * @author Budi Yanto, KNIME.com, Berlin, Germany
 * @author Ole Ostergaard, KNIME GmbH, Konstanz, Germany
 *
 */
public class S3Connection extends Connection {

	private static final NodeLogger LOGGER = NodeLogger.getLogger(S3Connection.class);

	private static final String ROLE_SESSION_NAME = "KNIME_S3_Connection";

	private final CloudConnectionInformation m_connectionInformation;

	private S3Client m_client;

	private S3TransferManager m_transferManager;

	private S3Presigner m_presigner;

	private List<String> m_bucketsCache;

	private boolean m_restrictedPermissions = false;

	/**
	 * Creates an S3 connection, given the connection information.
	 *
	 * @param connectionInformation The connection information for this S3 connection
	 */
	public S3Connection(final CloudConnectionInformation connectionInformation) {
		m_connectionInformation = connectionInformation;
		m_bucketsCache = new ArrayList<>();
	}

	@SuppressWarnings("resource")
    @Override
	public void open() throws Exception {
		if (!isOpen()) {
			LOGGER.info("Create a new AmazonS3Client in Region \"" + m_connectionInformation.getHost()
					+ "\" with connection timeout " + m_connectionInformation.getTimeout() + " milliseconds");

			try {
			    m_client = getS3Client(m_connectionInformation);
				resetCache();
				try {
				    // Try to fetch buckets. Will not work if ListAllMyBuckets is set to false
				    getBuckets();
				} catch (final S3Exception e){
				    if (Objects.equals(e.awsErrorDetails().errorCode(), "InvalidAccessKeyId")) {
				        throw new InvalidSettingsException("Check your Access Key ID / Secret Key.");
				    } else if (Objects.equals(e.awsErrorDetails().errorCode(), "AccessDenied")) {
				        // do nothing, see AP-8279
				        // This means that we do not have access to root level,
				        m_restrictedPermissions = true;
				    } else {
				        throw e;
				    }
				}
				m_presigner = getPresigner(m_connectionInformation);
				m_transferManager = S3TransferManager.builder()
				        .s3Client(getAsyncS3Client(m_connectionInformation)).build();
			} catch (final S3Exception ex) {
				close();
				throw ex;
			}
		}
	}

	private static S3Client getS3Client(final CloudConnectionInformation connInfo) {
	    final var clientConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(connInfo.getTimeout())).build();

        return S3Client.builder()
                .overrideConfiguration(clientConfig).region(Region.of(connInfo.getHost()))
                .credentialsProvider(AWSCredentialHelper.getCredentialProvider(connInfo, ROLE_SESSION_NAME))
                .build();
	}

	private static S3AsyncClient getAsyncS3Client(final CloudConnectionInformation connInfo) {
        return S3AsyncClient.crtBuilder()
                .region(Region.of(connInfo.getHost()))
                .credentialsProvider(AWSCredentialHelper.getCredentialProvider(connInfo, ROLE_SESSION_NAME))
                .build();
    }

	private static S3Presigner getPresigner(final CloudConnectionInformation connInfo) {
	    return S3Presigner.builder()
                .region(Region.of(connInfo.getHost()))
                .credentialsProvider(AWSCredentialHelper.getCredentialProvider(connInfo, ROLE_SESSION_NAME))
                .build();
    }

	@Override
	public boolean isOpen() {
		return m_client != null && m_transferManager != null && m_presigner != null;
	}

	@Override
	public void close() throws Exception {
		resetCache();
		if(m_transferManager != null) {
		    m_transferManager.close();
		}
		if (m_presigner != null) {
            m_presigner.close();
        }
		if(m_client != null) {
		    m_client.close();
		}
	}

	/**
	 * Get this connection's S3Client
	 *
	 * @return the connection's client
	 */
	public S3Client getClient() {
		return m_client;
	}

	/**
	 * Get this connection's TransferManager
	 * @return the connection's transfer manager
	 */
	public S3TransferManager getTransferManager() {
		return m_transferManager;
	}

	/**
	 * Get this connection's S3Presigner
	 *
	 * @return the connection's presigner
	 */
	public S3Presigner getPresigner() {
	    return m_presigner;
	}

	/**
	 * Get the List of this connection's cached buckets
	 * @return the list of this connection's cached buckets
	 * @throws S3Exception
	 */
	public List<String> getBuckets() throws S3Exception {
		if (m_bucketsCache == null) {
			m_bucketsCache = new ArrayList<>();
			final var response = m_client.listBuckets();
			for (final Bucket bucket : response.buckets()) {
				m_bucketsCache.add(bucket.name());
			}
		}
		return m_bucketsCache;
	}

	/**
	 * Validate the given buckets name
	 * @param bucketName the bucket name to be validated
	 * @throws Exception
	 */
	public void validateBucketName(final String bucketName) throws Exception {
		open();
		if (!getBuckets().contains(bucketName)) {
			throw new IllegalArgumentException("Not authorized to access the bucket: " + bucketName);
		}
	}

	/**
	 * Check whether the bucket is accessible from the client
	 * @param bucketName the bucket that should be checked
	 * @return <code>true</code> if the bucket is accessible, <code>false</code> otherwise
	 * @throws Exception
	 */
	public boolean isOwnBucket(final String bucketName) throws Exception {
		open();
		return getBuckets().contains(bucketName);
	}

	/**
	 * Reset this connection's bucket cache
	 */
	public void resetCache() {
		if (m_bucketsCache != null) {
			m_bucketsCache.clear();
		}
		m_bucketsCache = null;
	}

	/**
	 * Returns whether to use server side encryption.
	 *
	 * @return Whether to use server side encryption
	 */
	public boolean useSSEncryption() {
        return m_connectionInformation.useSSEncryption();

	}

	/**
	 * Returns whether or not the connection was created with credentials that have restricted access to S3.
	 * This could be bucket-specific access without list-buckets permissions.
	 *
	 * @return Whether or not the connection was created with credentials that have restricted access to S3.
	 */
	public boolean restrictedPermissions() {
	    return m_restrictedPermissions;
	}

}
