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
 *   Jul 19, 2016 (budiyanto): created
 */
package org.knime.cloud.aws.s3.filehandler;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.knime.base.filehandling.remote.files.ConnectionMonitor;
import org.knime.base.filehandling.remote.files.RemoteFile;
import org.knime.cloud.core.file.CloudRemoteFile;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.FileUtil;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

/**
 * Implementation of {@link CloudRemoteFile} for Amazon S3
 *
 * @author Budi Yanto, KNIME.com
 * @author Ole Ostergaard, KNIME.com GmbH
 */
public class S3RemoteFile extends CloudRemoteFile<S3Connection> {

	/**
	 * Create an S3 RemoteFile for the given URI, connection information and connection monitor.
	 *
	 * @param uri The RemoteFile's URI
	 * @param connectionInformation The RemoteFile's connection information
	 * @param connectionMonitor The RemotFile's connection monitor
	 */
	protected S3RemoteFile(final URI uri, final CloudConnectionInformation connectionInformation,
			final ConnectionMonitor<S3Connection> connectionMonitor) {
		this(uri, connectionInformation, connectionMonitor, null, null);
	}

	/**
	 * Creates an S3 RemoteFile for the given URI, connection information, connection monitor and S3ObjectSummary.
	 *
	 * @param uri The RemoteFile's URI
	 * @param connectionInformation The RemoteFile's connection information
	 * @param connectionMonitor The RemoteFile's connection monitor
	 * @param bucketName The RemoteFile's bucket name
	 * @param summary The RemoteFile's S3Object
	 */
	protected S3RemoteFile(final URI uri, final CloudConnectionInformation connectionInformation,
			final ConnectionMonitor<S3Connection> connectionMonitor, final String bucketName, final S3Object summary) {
		super(uri, connectionInformation, connectionMonitor);
		CheckUtils.checkArgumentNotNull(connectionInformation, "Connection information must not be null");
		m_containerName = bucketName;
		if (summary != null) {
			m_blobName = summary.key();
			m_lastModified = summary.lastModified().getEpochSecond();
			m_size = summary.size();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected S3Connection createConnection() {
		return new S3Connection((CloudConnectionInformation)getConnectionInformation());
	}

	private S3Client getClient() throws Exception {
		return getOpenedConnection().getClient();
	}

	private S3TransferManager getTransferManager() throws Exception {
		return getOpenedConnection().getTransferManager();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean doesContainerExist(final String containerName) throws Exception {
	    try {
            boolean exists = false;
            final S3Connection openedConnection = getOpenedConnection();
            if (openedConnection.restrictedPermissions()) {
                exists = true;
            } else {
                exists = openedConnection.isOwnBucket(containerName);
            }
        	return exists;
		} catch (S3Exception amazonException) {
		    throw new KnimeS3Exception(amazonException);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	protected boolean doestBlobExist(final String containerName, final String blobName) throws Exception {
	    try {
	        final var headRequest = HeadObjectRequest.builder().bucket(containerName)
	                .key(blobName).build();
	        try {
	            getClient().headObject(headRequest);
	            return true;
	        } catch (NoSuchKeyException noKeyException) {//NOSONAR
	            return false;
	        }
	    } catch (S3Exception amazonException) {
	        throw new KnimeS3Exception(amazonException);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	protected S3RemoteFile[] listRootFiles() throws Exception {
	    try {
	        final var response = getClient().listBuckets();
	        final List<Bucket> buckets = response.buckets();
	        if (buckets == null || buckets.isEmpty()) {
	            return new S3RemoteFile[0];
	        }
	        final S3RemoteFile[] files = new S3RemoteFile[buckets.size()];
	        for (int i = 0; i < files.length; i++) {
	            final URI uri = new URI(getURI().getScheme(), getURI().getUserInfo(), getURI().getHost(),
	                    getURI().getPort(), createContainerPath(buckets.get(i).name()), getURI().getQuery(),
	                    getURI().getFragment());
	            files[i] = new S3RemoteFile(uri, (CloudConnectionInformation)getConnectionInformation(),
	                    getConnectionMonitor());
	        }
	        return files;
	    } catch (S3Exception amazonException) {
	        throw new KnimeS3Exception(amazonException);
	    }
	}

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    protected S3RemoteFile[] listDirectoryFiles() throws Exception {
        S3RemoteFile[] files;
        try {
            final String bucketName = getContainerName();
            final String prefix = getBlobName();
            var request = ListObjectsV2Request.builder()
                    .bucket(bucketName).prefix(prefix).delimiter(DELIMITER).build();
            ListObjectsV2Response result;
            final List<S3RemoteFile> fileList = new ArrayList<>();
            do {
                result = getClient().listObjectsV2(request);
                for (final S3Object summary : result.contents()) {
                    if (!summary.key().equals(prefix)) {
                        final URI uri = new URI(getURI().getScheme(), getURI().getUserInfo(), getURI().getHost(),
                                getURI().getPort(), createContainerPath(bucketName) + summary.key(),
                                getURI().getQuery(), getURI().getFragment());
                        fileList.add(new S3RemoteFile(uri, (CloudConnectionInformation)getConnectionInformation(),
                                getConnectionMonitor(), bucketName, summary));
                    }
                }

                for (final CommonPrefix commPrefix : result.commonPrefixes()) {
                    final URI uri = new URI(getURI().getScheme(), getURI().getUserInfo(), getURI().getHost(),
                            getURI().getPort(), createContainerPath(bucketName) + commPrefix.prefix(),
                            getURI().getQuery(), getURI().getFragment());
                    fileList.add(new S3RemoteFile(uri, (CloudConnectionInformation)getConnectionInformation(),
                            getConnectionMonitor()));
                }
                request = request.toBuilder().continuationToken(result.nextContinuationToken()).build();
            } while (result.isTruncated());

            files = fileList.toArray(new S3RemoteFile[fileList.size()]);

        } catch (S3Exception e) {
            // Listing does not work, when bucket is in wrong region, return empty array of files -- see AP-6662
            files = new S3RemoteFile[0];
        }
        return files;
    }

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	protected long getBlobSize() throws Exception {
	    try {
	        final var headRequest = HeadObjectRequest.builder().bucket(getContainerName())
	                .key(getBlobName()).build();
            return getClient().headObject(headRequest).contentLength();
	    } catch (S3Exception amazonException) {
	        throw new KnimeS3Exception(amazonException);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	protected long getLastModified() throws Exception {
	    try {
	        final var headRequest = HeadObjectRequest.builder().bucket(getContainerName())
                    .key(getBlobName()).build();
	        return getClient().headObject(headRequest).lastModified().toEpochMilli();
	    } catch (S3Exception amazonException) {
	        throw new KnimeS3Exception(amazonException);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	protected boolean deleteContainer() throws Exception {
		try {
    	    for (final CloudRemoteFile<S3Connection> file : listFiles()) {
    			((S3RemoteFile) file).delete();
    		}
    		getClient().deleteBucket(DeleteBucketRequest.builder().bucket(getContainerName()).build());
    		return true;
		} catch (S3Exception amazonException) {
		    throw new KnimeS3Exception(amazonException);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean deleteDirectory() throws Exception {
		try {
    	    for (final CloudRemoteFile<S3Connection> file : listFiles()) {
    			((S3RemoteFile) file).delete();
    		}
    		return deleteBlob();
		} catch (S3Exception amazonException) {
		    throw new KnimeS3Exception(amazonException);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	protected boolean deleteBlob() throws Exception {
		try {
		    final var deleteRequest = DeleteObjectRequest.builder().bucket(getContainerName())
		            .key(getBlobName()).build();
    	    getClient().deleteObject(deleteRequest);
    		return true;
		} catch (S3Exception amazonException) {
		    throw new KnimeS3Exception(amazonException);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	protected boolean createContainer() throws Exception {
		try {
		    final var createRequest = CreateBucketRequest.builder().bucket(getContainerName()).build();
		    getClient().createBucket(createRequest);
		    return true;
		} catch (S3Exception amazonException) {
		    throw new KnimeS3Exception(amazonException);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	protected boolean createDirectory(final String dirName) throws Exception {
	    try {
	        // Create a PutObjectRequest passing the folder name
            // suffixed by the DELIMITER
	        final var reqBuilder = PutObjectRequest.builder()
                    .bucket(getContainerName())
                    .key(dirName);
            if (getConnection().useSSEncryption()) {
                // Add SSEncryption --> See AP-8823
                reqBuilder.serverSideEncryption(ServerSideEncryption.AES256);
            }
	        getClient().putObject(reqBuilder.build(), RequestBody.empty());
	        return true;
	    } catch (S3Exception amazonException) {
	        throw new KnimeS3Exception(amazonException);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("resource")
    @Override
	public InputStream openInputStream() throws Exception {
	    try {
    		final var request = GetObjectRequest.builder().bucket(getContainerName())
    		        .key(getBlobName()).build();
    		final var response = getClient().getObject(request);
    		return new BufferedInputStream(response);
	    } catch (S3Exception amazonException) {
	        throw new KnimeS3Exception(amazonException);
	    }
	}

	/**
	 * {@inheritDoc}
	 * This must not be used. The {@link #write(RemoteFile, ExecutionContext) write} is overwritten.
	 */
	@Override
	public OutputStream openOutputStream() throws Exception {
		throw new UnsupportedOperationException("openOutputStream must not be used for S3 connections.");
	}

	/**
     * Write the given remote file into this files location.
     *
     *
     * This method will overwrite the old file if it exists.
     *
     * @param remoteFile Source remote file
     * @param exec Execution context for <code>checkCanceled()</code> and
     *            <code>setProgress()</code>
     * @throws Exception If the operation could not be executed
     */
    @SuppressWarnings({"rawtypes", "resource"})
    @Override
    public void write(final RemoteFile remoteFile, final ExecutionContext exec) throws Exception {

        final var fileExtension = FilenameUtils.getExtension(remoteFile.getFullName());
        final var localFile = FileUtil.createTempFile(UUID.randomUUID().toString(),
            (StringUtils.isBlank(fileExtension) ? "" : ("." + fileExtension)), true);

        try (final var stream = new FileOutputStream(localFile)) {
            FileUtil.copy(remoteFile.openInputStream(), stream);
        } catch (final Exception ex) {
            localFile.delete(); // NOSONAR
            throw new RuntimeException(
                "Error while copying the remote input file to the local machine: " + ex.getMessage(), ex);
        }

        final var putBuilder = PutObjectRequest.builder()
                .bucket(getContainerName())
                .key(getBlobName());
        if (getConnection().useSSEncryption()) {
            putBuilder.serverSideEncryption(ServerSideEncryption.AES256);
        }
        long fileSize = remoteFile.getSize();
        final var uri = getURI().toString();
        final var transferListener =  new TransferListener() {

            long m_totalTransferred = 0;

            @Override
            public void bytesTransferred(final Context.BytesTransferred context) {
                m_totalTransferred += context.progressSnapshot().transferredBytes();
                exec.setProgress(((double) m_totalTransferred) / fileSize,//
                    () -> "Written: " + FileUtils.byteCountToDisplaySize(m_totalTransferred) + " to file " + uri);
            }
        };
        final var uploadFileRequest = UploadFileRequest.builder()
                .putObjectRequest(putBuilder.build())
                .source(localFile)
                .addTransferListener(transferListener)
                .build();
        try {
            final var fileUpload = getTransferManager().uploadFile(uploadFileRequest);
            fileUpload.completionFuture().join();
        } catch (InterruptedException e) {
            // check if canceled, otherwise throw exception
            exec.checkCanceled();
            throw e;
        } catch (S3Exception amazonException) {
            throw new KnimeS3Exception(amazonException);
        } finally {
            localFile.delete(); // NOSONAR
        }
    }

	@Override
	protected void resetCache() throws Exception {
		super.resetCache();
		getOpenedConnection().resetCache();
	}

    @Override
    public URI getHadoopFilesystemURI() throws Exception {
        // s3://<containername>/<path>
        final String blobName = Optional.ofNullable(getBlobName()).orElseGet(() -> "");
        return new URI("s3",
            null,
            getContainerName(),
            -1,
            DELIMITER + blobName,
            null,
            null);
    }
}
