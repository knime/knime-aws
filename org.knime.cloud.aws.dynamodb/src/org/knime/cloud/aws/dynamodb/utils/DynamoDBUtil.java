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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.knime.cloud.aws.dynamodb.settings.DynamoDBSettings;
import org.knime.cloud.aws.dynamodb.settings.DynamoDBTableSettings;
import org.knime.cloud.core.util.port.CloudConnectionInformation;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.util.KnimeEncryption;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.util.SdkAutoConstructList;
import software.amazon.awssdk.core.util.SdkAutoConstructMap;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue.Builder;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * General helper methods for DynamoDB API.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
public final class DynamoDBUtil {

    private DynamoDBUtil() { }

    /**
     * String representing the binary data type for DynamoDB.
     */
    public static final String BINARY_TYPE = "B";

    /**
     * String representing the boolean data type for DynamoDB.
     */
    public static final String BOOLEAN_TYPE = "BOOL";

    /**
     * String representing the binary set data type for DynamoDB.
     */
    public static final String BINARY_SET_TYPE = "BS";

    /**
     * String representing the list data type for DynamoDB.
     */
    public static final String LIST_TYPE = "L";

    /**
     * String representing the map data type for DynamoDB.
     */
    public static final String MAP_TYPE = "M";

    /**
     * String representing the number data type for DynamoDB.
     */
    public static final String NUMBER_TYPE = "N";

    /**
     * String representing the number set data type for DynamoDB.
     */
    public static final String NUMBER_SET_TYPE = "NS";

    /**
     * String representing the null data type for DynamoDB.
     */
    public static final String NULL_TYPE = "NULL";

    /**
     * String representing the string data type for DynamoDB.
     */
    public static final String STRING_TYPE = "S";

    /**
     * String representing the string set data type for DynamoDB.
     */
    public static final String STRING_SET_TYPE = "SS";

    /**
     * All supported DynamoDB types.
     * @return an array of supported DynamoDB types, represented as String
     */
    public static String[] getTypes() {
        return new String[] {
                BINARY_TYPE, BOOLEAN_TYPE, BINARY_SET_TYPE, LIST_TYPE, MAP_TYPE,
                NUMBER_TYPE, NUMBER_SET_TYPE, NULL_TYPE, STRING_TYPE, STRING_SET_TYPE};
    }

    /**
     * @return an array of human readable type names of scalar DynamoDB types
     */
    public static String[] getHumanReadableKeyTypes() {
        return new String[] {"String", "Number", "Binary"};
    }

    /**
     * Converts a human readable string for type selection to a <code>ScalarAttributeType</code>.
     * @param t the type as human readable string
     * @return a corresponding <code>ScalarAttributeType</code>
     */
    public static ScalarAttributeType humanReadableToType(final String t) {
        if (t.equals("String")) {
            return ScalarAttributeType.S;
        } else if (t.equals("Binary")) {
            return ScalarAttributeType.B;
        } else {
            return ScalarAttributeType.N;
        }
    }

    /**
     * Converts a <code>ScalarAttributeType</code> to a human readable string.
     * @param t the <code>ScalarAttributeType</code> to convert
     * @return a corresponding human readable string, i.e. String, Binary, Number
     */
    public static String keyTypeToHumanReadable(final ScalarAttributeType t) {
        if (t == ScalarAttributeType.S) {
            return "String";
        } else if (t == ScalarAttributeType.B) {
            return "Binary";
        } else {
            return "Number";
        }
    }

    /**
     * Checks if the given attribute value is a string.
     * @param val the attribute value to check
     * @return true if the value is a string
     */
    public static boolean isString(final AttributeValue val) {
        return val.getValueForField(DynamoDBUtil.STRING_TYPE, String.class).isPresent();
    }

    /**
     * Checks if the given attribute value is a number.
     * @param val the attribute value to check
     * @return true if the value is a number
     */
    public static boolean isNumber(final AttributeValue val) {
        return val.getValueForField(DynamoDBUtil.NUMBER_TYPE, String.class).isPresent();
    }

    /**
     * Checks if the given attribute value is a boolean.
     * @param val the attribute value to check
     * @return true if the value is a boolean
     */
    public static boolean isBoolean(final AttributeValue val) {
        return val.getValueForField(DynamoDBUtil.BOOLEAN_TYPE, Boolean.class).isPresent();
    }

    /**
     * Checks if the given attribute value is binary.
     * @param val the attribute value to check
     * @return true if the value is binary
     */
    public static boolean isBinary(final AttributeValue val) {
        return val.getValueForField(DynamoDBUtil.BINARY_TYPE, String.class).isPresent();
    }

    /**
     * Checks if the given attribute value is null.
     * @param val the attribute value to check
     * @return true if the value is null
     */
    public static boolean isNul(final AttributeValue val) {
        return val.getValueForField(DynamoDBUtil.NULL_TYPE, Boolean.class).isPresent();
    }

    /**
     * Checks if the given attribute value is a list.
     * @param val the attribute value to check
     * @return true if the value is a list
     */
    public static boolean isList(final AttributeValue val) {
        // SdkAutoConstructList is only used as placeholder, so if it is present, it is not a list
        return !(val.l() instanceof SdkAutoConstructList<?>);
    }

    /**
     * Checks if the given attribute value is a string set.
     * @param val the attribute value to check
     * @return true if the value is a string set
     */
    public static boolean isStringSet(final AttributeValue val) {
        // DynamoDB sets cannot be empty, so if it is, it is not a set
        return !val.ss().isEmpty();
    }

    /**
     * Checks if the given attribute value is a number set.
     * @param val the attribute value to check
     * @return true if the value is a number set
     */
    public static boolean isNumberSet(final AttributeValue val) {
     // DynamoDB sets cannot be empty, so if it is, it is not a set
        return !val.ns().isEmpty();
    }

    /**
     * Checks if the given attribute value is a binary set.
     * @param val the attribute value to check
     * @return true if the value is a binary set
     */
    public static boolean isBinarySet(final AttributeValue val) {
     // DynamoDB sets cannot be empty, so if it is, it is not a set
        return !val.bs().isEmpty();
    }

    /**
     * Checks if the given attribute value is a map.
     * @param val the attribute value to check
     * @return true if the value is a map
     */
    public static boolean isMap(final AttributeValue val) {
        // SdkAutoConstructMap is only used as placeholder, so if it is present, it is not a map
        return !(val.m() instanceof SdkAutoConstructMap<?, ?>);
    }

    /**
     * Creates a new DynamoDB client from the supplied settings.
     *
     * @param settings the settings for the connection
     * @param con optional connection settings from a port
     * @return a DynamoDbClient for reading and writing from/to DynamoDB
     * @throws Exception when the credentials format are invalid or cannot be decrypted
     */
    public static DynamoDbClient createClient(final DynamoDBSettings settings,
            final CloudConnectionInformation con) throws Exception {
        // Client is either created from a port object or from the settings
    	final String endpoint = settings.getEndpoint();
        final AwsCredentialsProvider credentialProvider;
        final Region region;
        if (con != null) {
        	region = Region.of(con.getHost());
        	credentialProvider = getCredentialProvider(con);
        } else {
            region = settings.getRegion();
            if (settings.isCredentialsGiven()) {
            	credentialProvider = StaticCredentialsProvider
                        .create(AwsBasicCredentials.create(settings.getAccessKey(), settings.getSecretKey()));
            } else {
            	credentialProvider = DefaultCredentialsProvider.create();
            }
        }
        return createClient(credentialProvider, endpoint, region);
    }

	/**
	 * @param credentialProvider the {@link AwsCredentialsProvider} to use
	 * @param endpoint the endpoint to use or <code>null</code> for the default
	 * @param region the AWS region to use
	 * @return the {@link DynamoDbClient}
	 */
	private static DynamoDbClient createClient(final AwsCredentialsProvider credentialProvider, final String endpoint,
			final Region region) {
		final DynamoDbClientBuilder builder =
    			DynamoDbClient.builder().region(region).credentialsProvider(credentialProvider);
        if (endpoint != null && endpoint.trim().length() > 0) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
	}

	private static AwsCredentialsProvider getCredentialProvider(final CloudConnectionInformation con) {
		final AwsCredentialsProvider credentialProvider;
		final AwsCredentialsProvider conCredentialProvider;
		if (con.useKeyChain()) {
			conCredentialProvider = DefaultCredentialsProvider.create();
		} else if (con.isUseAnonymous()) {
			conCredentialProvider = AnonymousCredentialsProvider.create();
		} else {
		    final String accessKeyId = con.getUser();
		    String secretAccessKey;
			try {
				secretAccessKey = KnimeEncryption.decrypt(con.getPassword());
			} catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException | IOException e) {
				throw new IllegalStateException(e);
			}
		    conCredentialProvider = StaticCredentialsProvider
		            .create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
		}
		if (con.switchRole()) {
			credentialProvider = getRoleSwitchCredentialProvider(con, conCredentialProvider);
		} else {
			credentialProvider = conCredentialProvider;
		}
		return credentialProvider;
	}

	@SuppressWarnings("boxing")
	private static AwsCredentialsProvider getRoleSwitchCredentialProvider(final CloudConnectionInformation con,
			final AwsCredentialsProvider credentialProvider) {
		final AssumeRoleRequest asumeRole = AssumeRoleRequest.builder().roleArn(buildARN(con)).durationSeconds(3600)
		.roleSessionName("KNIME_DynamoDB_Connection").build();


		final StsClient stsClient =
				StsClient.builder().region(Region.of(con.getHost())).credentialsProvider(credentialProvider).build();

		final StsAssumeRoleCredentialsProvider roleSwitchCredentialProvider =
				StsAssumeRoleCredentialsProvider.builder().stsClient(stsClient).refreshRequest(asumeRole)
				.asyncCredentialUpdateEnabled(true).build();
		return roleSwitchCredentialProvider;
    }


	private static String buildARN(final CloudConnectionInformation connectionInformation) {
	    return "arn:aws:iam::" + connectionInformation.getSwitchRoleAccount() + ":role/" + connectionInformation.getSwitchRoleName();
	}

    /**
	 * Lists all tables for the given account.
	 * @param con connection information for the user's AWS account
	 * @param limit the maximum number of table names to retrieve
	 * @return a list of table names
	 */
	public static List<String> getTableNames(final CloudConnectionInformation con, final int limit) {
	    return getTableNames(Region.of(con.getHost()), null, getCredentialProvider(con), limit);
	}

	/**
     * Lists all tables for the given account.
     * @param region the region in which the tables reside
     * @param endpoint the custom endpoint (optional)
     * @param accessKey the user's access kex
     * @param secretKey the user's secret key
     * @param limit the maximum number of table names to retrieve
     * @return a list of table names
     */
    public static List<String> getTableNames(final Region region, final String endpoint,
            final String accessKey, final String secretKey, final int limit) {
    	final AwsCredentialsProvider credentialProvider;
    	if (accessKey != null) {
    		credentialProvider = StaticCredentialsProvider
                    .create(AwsBasicCredentials.create(accessKey, secretKey));
        } else {
        	credentialProvider = DefaultCredentialsProvider.create();
        }
    	return getTableNames(region, endpoint, credentialProvider, limit);
    }

    private static List<String> getTableNames(final Region region, final String endpoint,
    		final AwsCredentialsProvider credentialProvider, final int limit) {
    	final DynamoDbClient ddb = createClient(credentialProvider, endpoint, region);
        final ListTablesResponse tables = ddb.listTables(ListTablesRequest.builder().limit(limit).build());
        return tables.tableNames();
	}

	/**
     * Queries a DynamoDB instance for a table description.
     * @param tblSettings the table settings containing the info to identify the table
     * @return a table description including key schema information
     * @throws InvalidSettingsException when the request was not successful
     * @throws ResourceNotFoundException when the table does not exist
     */
    public static TableDescription describeTable(final DynamoDBTableSettings tblSettings)
            throws InvalidSettingsException {
        return describeTable(tblSettings, true);
    }

    /**
     * Queries a DynamoDB instance for a table description.
     * @param tblSettings the table settings containing the info to identify the table
     * @param throwOnNotFound if true, an exception is thrown if the table does not exist. Otherwise null is returned.
     * @return a table description including key schema information
     * @throws InvalidSettingsException when the request was not successful
     * @throws ResourceNotFoundException when the table does not exist and {@code throwOnNotFound} is true
     */
    public static TableDescription describeTable(final DynamoDBTableSettings tblSettings, final boolean throwOnNotFound)
            throws InvalidSettingsException {
        return describeTable(tblSettings.getTableName(), tblSettings.getRegion(),
                tblSettings.getEndpoint(), tblSettings.getAccessKey(), tblSettings.getSecretKey(), throwOnNotFound);
    }

    /**
	 * Queries a DynamoDB instance for a table description.
	 * @param tableName the name of the table to get a description for
	 * @param region the region the table is in
	 * @param endpoint a custom endpoint or null/empty string for default endpoint
	 * @param accessKey the access key of the user accessing the API
	 * @param secretKey the secret key of the user accessing the API
	 * @return a table description including key schema information
	 * @throws InvalidSettingsException when the request was not successful
	 * @throws ResourceNotFoundException when the table does not exist
	 */
	public static TableDescription describeTable(final String tableName, final Region region, final String endpoint,
	        final String accessKey, final String secretKey) throws InvalidSettingsException {
	    return describeTable(tableName, region, endpoint, accessKey, secretKey, true);
	}

	/**
	 * Queries a DynamoDB instance for a table description.
	 * @param tableName the name of the table to get a description for
	 * @param region the region the table is in
	 * @param endpoint a custom endpoint or null/empty string for default endpoint
	 * @param accessKey the access key of the user accessing the API
	 * @param secretKey the secret key of the user accessing the API
	 * @param throwOnNotFound if true, an exception is thrown if the table does not exist. Otherwise null is returned.
	 * @return a table description including key schema information
	 * @throws InvalidSettingsException when the request was not successful
	 * @throws ResourceNotFoundException when the table does not exist and {@code throwOnNotFound} is true
	 */
	public static TableDescription describeTable(final String tableName, final Region region, final String endpoint,
	        final String accessKey, final String secretKey, final boolean throwOnNotFound)
	                throws InvalidSettingsException {

		final AwsCredentialsProvider credentialProvider;
	    if (accessKey != null) {
	        credentialProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
	    } else {
	    	credentialProvider =  DefaultCredentialsProvider.create();
	    }
	    return describeTable(tableName, region, endpoint, credentialProvider, throwOnNotFound);
	}

	/**
	 * Queries a DynamoDB instance for a table description.
	 * @param tableName the table to get a description for
	 * @param con the connection information
	 * @return a table description including key schema information
	 * @throws InvalidSettingsException when the request was not successful
	 * @throws ResourceNotFoundException when the table does not exist
	 */
	public static TableDescription describeTable(final String tableName, final CloudConnectionInformation con)
	        throws InvalidSettingsException {
	    return describeTable(tableName,con, true);
	}

	/**
     * Queries a DynamoDB instance for a table description.
     * @param tableName the table to get a description for
     * @param con the connection information
     * @param throwOnNotFound if true, an exception is thrown if the table does not exist. Otherwise null is returned.
     * @return a table description including key schema information
     * @throws InvalidSettingsException when the request was not successful
     * @throws ResourceNotFoundException when the table does not exist and {@code throwOnNotFound} is true
     */
    public static TableDescription describeTable(final String tableName, final CloudConnectionInformation con,
            final boolean throwOnNotFound) throws InvalidSettingsException {
        return describeTable(tableName, Region.of(con.getHost()), null, getCredentialProvider(con), throwOnNotFound);
    }

    private static TableDescription describeTable(final String tableName, final Region region, final String endpoint,
			final AwsCredentialsProvider credentialProvider, final boolean throwOnNotFound)
	                throws InvalidSettingsException {
	    final DynamoDbClient ddb = createClient(credentialProvider, endpoint, region);
	    DescribeTableResponse response = null;
	    try {
	        response = ddb.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
	    } catch (final ResourceNotFoundException e) {
	        if (throwOnNotFound) {
	            throw e;
	        } else {
	            return null;
	        }
	    }
	    if (!response.sdkHttpResponse().isSuccessful()) {
	        final Optional<String> statusText = response.sdkHttpResponse().statusText();
	        throw new InvalidSettingsException(statusText.orElse("Request for table metadata was unsuccessful"));
	    }
	    return response.table();
	}

	/**
     * Converts a string of the form {"&lt;type>" : "&lt;value>"} to an
     * AttributeValue.
     *
     * @param s
     *            the string to convert
     * @return an AttributeValue represented by the given string
     * @throws InvalidSettingsException
     *             when the string is malformed
     */
    public static AttributeValue stringToAttributeValue(final String s) throws InvalidSettingsException {
        String jsonStr = s;
        if (!jsonStr.trim().startsWith("{")) {
            jsonStr = String.format("{%s}", jsonStr);
        }
        final ObjectMapper mapper = new ObjectMapper();
        JsonNode node;
        try {
            node = mapper.readTree(jsonStr);
        } catch (final IOException e) {
            throw new InvalidSettingsException("The given attribute value is not a JSON string.");
        }
        return jsonNodeToAttributeValue(node);
    }

    private static AttributeValue jsonNodeToAttributeValue(final JsonNode node) throws InvalidSettingsException {
        final Iterator<Entry<String, JsonNode>> f = node.fields();
        if (f.hasNext()) {
            final Entry<String, JsonNode> entry = f.next();
            final String type = entry.getKey();
            final JsonNode value = entry.getValue();
            return toAttribute(type, value);
        }
        return null;
    }

    /**
     * Converts a <code>JsonNode</code> to a DynamoDB attribute value.
     * @param type String representation of the type the <code>JsonNode</code> represents
     * @param node the <code>JsonNode</code> containing the data
     * @return a DynamoDB attribute value
     * @throws InvalidSettingsException when the <code>JsonNode</code> does not match the type
     */
    public static AttributeValue toAttribute(final String type, final JsonNode node) throws InvalidSettingsException {
        final Builder builder = AttributeValue.builder();
        switch (type.toUpperCase()) {
        case DynamoDBUtil.BINARY_TYPE:
            return builder.b(SdkBytes.fromString(node.asText(), Charset.defaultCharset())).build();
        case DynamoDBUtil.BOOLEAN_TYPE:
            return builder.bool(node.asBoolean()).build();
        case DynamoDBUtil.BINARY_SET_TYPE:
            if (!node.isArray()) {
                throw new InvalidSettingsException("A binary set must be an array of strings.");
            }
            final List<SdkBytes> bytesValues = new ArrayList<>();
            final Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                final JsonNode element = elements.next();
                if (!element.isTextual()) {
                    throw new InvalidSettingsException("A binary set must be an array of strings.");
                }
                bytesValues.add(SdkBytes.fromString(element.asText(), Charset.defaultCharset()));
            }
            return builder.bs(bytesValues).build();
        case DynamoDBUtil.LIST_TYPE:
            if (!node.isArray()) {
                throw new InvalidSettingsException("A list must be an array of objects.");
            }
            final List<AttributeValue> listValues = new ArrayList<>();
            final Iterator<JsonNode> listElements = node.elements();
            while (listElements.hasNext()) {
                final JsonNode element = listElements.next();
                listValues.add(jsonNodeToAttributeValue(element));
            }
            return builder.l(listValues).build();
        case DynamoDBUtil.MAP_TYPE:
            if (!node.isObject()) {
                throw new InvalidSettingsException("A map must be an object.");
            }
            final Map<String, AttributeValue> map = new HashMap<>();
            final Iterator<Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                final Entry<String, JsonNode> field = fields.next();
                if (!field.getValue().isObject()) {
                    throw new InvalidSettingsException("A map must contain only objects.");
                }
                map.put(field.getKey(), jsonNodeToAttributeValue(field.getValue()));
            }
            return builder.m(map).build();
        case DynamoDBUtil.NUMBER_TYPE:
            return builder.n(Double.toString(node.asDouble())).build();
        case DynamoDBUtil.NUMBER_SET_TYPE:
            if (!node.isArray()) {
                throw new InvalidSettingsException("A number set must be an array of strings.");
            }
            final List<String> numbers = new ArrayList<>();
            final Iterator<JsonNode> numberElements = node.elements();
            while (numberElements.hasNext()) {
                final JsonNode element = numberElements.next();
                if (!element.isTextual()) {
                    throw new InvalidSettingsException("A number set must only contain numbers formatted as strings.");
                }
                numbers.add(element.asText());
            }
            return builder.ns(numbers).build();
        case DynamoDBUtil.NULL_TYPE:
            return builder.nul(node.asBoolean()).build();
        case DynamoDBUtil.STRING_TYPE:
            return builder.s(node.asText()).build();
        case DynamoDBUtil.STRING_SET_TYPE:
            if (!node.isArray()) {
                throw new InvalidSettingsException("A string set must be an array of strings.");
            }
            final List<String> strings = new ArrayList<>();
            final Iterator<JsonNode> strElements = node.elements();
            while (strElements.hasNext()) {
                final JsonNode element = strElements.next();
                if (!element.isTextual()) {
                    throw new InvalidSettingsException("A string set must only contain strings.");
                }
                strings.add(element.asText());
            }
            return builder.ss(strings).build();
        default:
            throw new InvalidSettingsException("No valid type given");
        }
    }

    /**
     * Converts a DynamoDB attribute value to a <code>JsonValue</code> or primitive object.
     * @param val the attribute value to convert
     * @return an object representing the attribute value
     */
    public static Object attributeValueToJsonObject(final AttributeValue val) {
        if (val.s() != null) {
            return val.s();
        } else if (val.n() != null) {
            return Double.parseDouble(val.n());
        } else if (!val.ss().isEmpty()) {
            final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            for (final String s : val.ss()) {
                arrayBuilder.add(s);
            }
            return arrayBuilder.build();
        } else if (val.bool() != null) {
            return val.bool();
        } else if (val.b() != null) {
            return val.b().asString(Charset.defaultCharset());
        } else if (!val.bs().isEmpty()) {
            final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            for (final SdkBytes bytes : val.bs()) {
                arrayBuilder.add(bytes.asString(Charset.defaultCharset()));
            }
            return arrayBuilder.build();
        } else if (val.nul() != null) {
            return null;
        } else if (!val.l().isEmpty()) {
            final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            for (final AttributeValue v : val.l()) {
                final Object o = attributeValueToJsonObject(v);
                if (o instanceof String) {
                    arrayBuilder.add((String) o);
                } else if (o instanceof Boolean) {
                    arrayBuilder.add((Boolean) o);
                } else if (o instanceof Double) {
                    arrayBuilder.add((Double) o);
                } else if (o instanceof JsonValue) {
                    arrayBuilder.add((JsonValue) o);
                }
            }
            return arrayBuilder.build();
        } else if (!val.m().isEmpty()) {
            final JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            for (final Entry<String, AttributeValue> e : val.m().entrySet()) {
                final String key = e.getKey();
                final Object o = attributeValueToJsonObject(e.getValue());
                if (o instanceof String) {
                    objBuilder.add(key, (String) o);
                } else if (o instanceof Boolean) {
                    objBuilder.add(key, (Boolean) o);
                } else if (o instanceof Double) {
                    objBuilder.add(key, (Double) o);
                } else if (o instanceof JsonValue) {
                    objBuilder.add(key, (JsonValue) o);
                }
            }
            return objBuilder.build();
        } else if (!val.ns().isEmpty()) {
            final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            for (final String s : val.ns()) {
                arrayBuilder.add(Double.parseDouble(s));
            }
            return arrayBuilder.build();
        } else {
            return null;
        }
    }

    /**
     * Converts a string representing a scalar DynamoDB type to an attribute value.
     * @param value the string representation of the value
     * @param type the data type (B, S, or N)
     * @return an attribute value representing the given value
     */
    public static AttributeValue getKeyConditionAttrValue(final String value, final ScalarAttributeType type) {
        final software.amazon.awssdk.services.dynamodb.model.AttributeValue.Builder builder = AttributeValue.builder();
        if (type == ScalarAttributeType.S) {
            return builder.s(value).build();
        } else if (type == ScalarAttributeType.N) {
            return builder.n(value).build();
        } else if (type == ScalarAttributeType.B) {
            return builder.b(SdkBytes.fromString(value, Charset.defaultCharset())).build();
        }
        throw new IllegalArgumentException("Only types S, B and N are supported.");
    }

    /**
     * Converts a <code>JsonValue</code> to a DynamoDB attribute value.
     * @param jsonValue the <code>JsonNode</code> to convert
     * @return a matching attribute value
     */
    public static AttributeValue jsonValueToAttributeValue(final JsonValue jsonValue) {
        if (jsonValue instanceof JsonString) {
            return AttributeValue.builder().s(((JsonString)jsonValue).getString()).build();
        } else if (jsonValue instanceof JsonNumber) {
            return AttributeValue.builder().n(Double.toString(((JsonNumber)jsonValue).doubleValue())).build();
        } else if (jsonValue == JsonValue.TRUE) {
            return AttributeValue.builder().bool(true).build();
        } else if (jsonValue == JsonValue.FALSE) {
            return AttributeValue.builder().bool(false).build();
        } else if (jsonValue == JsonValue.NULL) {
            return AttributeValue.builder().nul(true).build();
        } else if (jsonValue instanceof JsonObject) {
            final Map<String, AttributeValue> value = new HashMap<>();
            final JsonObject obj = (JsonObject)jsonValue;
            for (final Entry<String, JsonValue> e : obj.entrySet()) {
                value.put(e.getKey(), jsonValueToAttributeValue(e.getValue()));
            }
            return AttributeValue.builder().m(value).build();
        } else if (jsonValue instanceof JsonArray) {
            final JsonArray arr = (JsonArray)jsonValue;
            final List<AttributeValue> value = new ArrayList<>();
            for (final JsonValue element : arr) {
                value.add(jsonValueToAttributeValue(element));
            }
            return AttributeValue.builder().l(value).build();
        }
        throw new IllegalArgumentException("Unknown JsonValue type");
    }
}
