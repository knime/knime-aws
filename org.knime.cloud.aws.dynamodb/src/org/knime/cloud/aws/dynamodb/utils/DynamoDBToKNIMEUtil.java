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

import java.nio.charset.Charset;
import java.util.stream.Collectors;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataType;
import org.knime.core.data.collection.CollectionCellFactory;
import org.knime.core.data.collection.ListCell;
import org.knime.core.data.collection.SetCell;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.json.JSONCell;
import org.knime.core.data.json.JSONCellFactory;

import jakarta.json.JsonValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Helper class for converting data from DynamoDB to KNIME data types.
 * @author Alexander Fillbrunn, University of Konstanz
 *
 */
public final class DynamoDBToKNIMEUtil {

    private DynamoDBToKNIMEUtil() { }
    
    /**
     * Finds a fitting KNIME data type from a value retrieved from DynamoDB.
     * @param val the value to get a KNIME type for
     * @return a KNIME data type for the 
     */
    public static DataType attributeValueToType(final AttributeValue val) {
        if (val == null) {
            throw new IllegalArgumentException("Argument val must not be null.");
        } else if (DynamoDBUtil.isString(val)) {
            return StringCell.TYPE;
        } else if (DynamoDBUtil.isNumber(val)) {
            return DoubleCell.TYPE;
        } else if (DynamoDBUtil.isStringSet(val)) {
            return SetCell.getCollectionType(StringCell.TYPE);
        } else if (DynamoDBUtil.isBoolean(val)) {
            return BooleanCell.TYPE;
        } else if (DynamoDBUtil.isBinary(val)) {
            return StringCell.TYPE;
        } else if (DynamoDBUtil.isBinarySet(val)) {
            return SetCell.getCollectionType(StringCell.TYPE);
        } else if (DynamoDBUtil.isNul(val)) {
            return null;
        } else if (DynamoDBUtil.isNumberSet(val)) {
            return SetCell.getCollectionType(DoubleCell.TYPE);
        } else if (DynamoDBUtil.isList(val)) {
            return ListCell.getCollectionType(DataType.getType(DataCell.class));
        } else if (DynamoDBUtil.isMap(val)) {
            return JSONCell.TYPE;
        } else {
            throw new IllegalArgumentException("AttributeValues of that type are not supported.");
        }
    }

    /**
     * Converts a DynamoDB attribute value to a KNIME data cell.
     * @param val the value to convert to a KNIME cell
     * @return a KNIME cell representing the given attribute value
     */
    public static DataCell attributeValueToDataCell(final AttributeValue val) {
        if (val == null) {
            return DataType.getMissingCell();
        } else if (DynamoDBUtil.isString(val)) {
            return new StringCell(val.s());
        } else if (DynamoDBUtil.isNumber(val)) {
            return new DoubleCell(Double.parseDouble(val.n()));
        } else if (DynamoDBUtil.isStringSet(val)) {
            return CollectionCellFactory
                    .createSetCell(val.ss().stream().map(s -> new StringCell(s)).collect(Collectors.toList()));
        } else if (DynamoDBUtil.isBoolean(val)) {
            return val.bool() ? BooleanCell.TRUE : BooleanCell.FALSE;
        } else if (DynamoDBUtil.isBinary(val)) {
            return new StringCell(val.b().asString(Charset.defaultCharset()));
        } else if (DynamoDBUtil.isBinarySet(val)) {
            return CollectionCellFactory.createSetCell(
                    val.bs().stream()
                        .map(bytes -> new StringCell(bytes.asString(Charset.defaultCharset())))
                        .collect(Collectors.toList()));
        } else if (DynamoDBUtil.isNul(val)) {
            return DataType.getMissingCell();
        } else if (DynamoDBUtil.isNumberSet(val)) {
            return CollectionCellFactory.createSetCell(
                    val.ns().stream().map(s -> new DoubleCell(Double.parseDouble(s))).collect(Collectors.toList()));
        } else if (DynamoDBUtil.isList(val)) {
            return CollectionCellFactory.createListCell(
                    val.l().stream().map(av -> attributeValueToDataCell(av)).collect(Collectors.toList()));
        } else if (DynamoDBUtil.isMap(val)) {
            return JSONCellFactory.create((JsonValue) DynamoDBUtil.attributeValueToJsonObject(val));
        }  else {
            return DataType.getMissingCell();
        }
    }
}
