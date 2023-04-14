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
 *   Mar 21, 2018 (oole): created
 */
package org.knime.cloud.aws.s3.filehandler;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.lang3.StringUtils;

import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Exception to make the {@link S3Exception}'s more readable
 *
 * @author Ole Ostergaard
 */
final class KnimeS3Exception extends Exception {
    private static final long serialVersionUID = 1L;

    /** Standard see log message shown in exceptions.*/
    public static final String SEE_LOG_SNIPPET = " (full details are in the KNIME log file)";

    /** Optional stack trace of original exception. */
    private final String m_stackTrace;

    /**
     * Constructor taking an {@link S3Exception} and adding some explanatory error message. The full Stack and
     * original exception will be logged and will show up in the KNIME log.
     *
     * @param amazonException The original exception that caused the error. You can assume that this exception will be
     *            logged automatically and will show up in the KNIME log.
     */
    KnimeS3Exception(final S3Exception amazonException) {
        super(getExplanation(amazonException) + SEE_LOG_SNIPPET, amazonException);
        m_stackTrace = toStringStackTrace(amazonException);
    }

    /** @return Stack trace of given cause as string. */
    private static String toStringStackTrace(final Throwable cause) {
        if (cause == null) {
            return null;
        } else {
            try(final StringWriter sw = new StringWriter();
                    final PrintWriter pw = new PrintWriter(sw);) {
                cause.printStackTrace(pw);
                return sw.toString();
            } catch (IOException e) {
                return "Unable to print stack trace for Throwable: " + cause.getClass().getName()
                        + ". Exception: " + e.getMessage();
            }
        }
    }

    @Override
    public void printStackTrace(final PrintStream s) {
        if (m_stackTrace != null) {
            s.append(m_stackTrace);
        } else {
            super.printStackTrace(s);
        }
    }

    @Override
    public void printStackTrace(final PrintWriter s) {
        if (m_stackTrace != null) {
            s.append(m_stackTrace);
        } else {
            super.printStackTrace(s);
        }
    }

    private static String getExplanation(final S3Exception exception) {
        final var sdkHttpResponse = exception.awsErrorDetails().sdkHttpResponse();

        var errorCode = exception.awsErrorDetails().errorCode();
        if (StringUtils.isBlank(errorCode)
                && sdkHttpResponse.statusText().isPresent()) {
            errorCode = sdkHttpResponse.statusText().get(); //NOSONAR
        }
        final var errorMessage = exception.awsErrorDetails().errorMessage();
        final var statusCode = String.valueOf(exception.statusCode());
        final var builder = new StringBuilder(statusCode + " " + errorCode + ". ");

        if (errorCode.contains("Forbidden")) {
            builder.append("You might not have access to the given location or it might not exist.");
        } else if (errorCode.contains("AccessDenied")) {
            builder.append("Your permissions do not allow this operation.");
        } else {
            builder.append(StringUtils.trimToEmpty(errorMessage));
        }
        return builder.toString();
    }
}
