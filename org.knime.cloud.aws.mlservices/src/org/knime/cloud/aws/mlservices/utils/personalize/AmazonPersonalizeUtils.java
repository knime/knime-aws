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
 *   Oct 30, 2019 (Simon Schmid, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.cloud.aws.mlservices.utils.personalize;

import java.util.List;

import org.knime.cloud.aws.mlservices.nodes.personalize.AmazonIdentityManagementConnection;
import org.knime.cloud.core.util.port.CloudConnectionInformation;

import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListRolesRequest;
import software.amazon.awssdk.services.iam.model.ListRolesResponse;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.personalize.PersonalizeClient;
import software.amazon.awssdk.services.personalize.model.CampaignSummary;
import software.amazon.awssdk.services.personalize.model.DatasetGroupSummary;
import software.amazon.awssdk.services.personalize.model.DatasetSchemaSummary;
import software.amazon.awssdk.services.personalize.model.ListCampaignsRequest;
import software.amazon.awssdk.services.personalize.model.ListCampaignsResponse;
import software.amazon.awssdk.services.personalize.model.ListDatasetGroupsRequest;
import software.amazon.awssdk.services.personalize.model.ListDatasetGroupsResponse;
import software.amazon.awssdk.services.personalize.model.ListRecipesRequest;
import software.amazon.awssdk.services.personalize.model.ListRecipesResponse;
import software.amazon.awssdk.services.personalize.model.ListSchemasRequest;
import software.amazon.awssdk.services.personalize.model.ListSchemasResponse;
import software.amazon.awssdk.services.personalize.model.ListSolutionVersionsRequest;
import software.amazon.awssdk.services.personalize.model.ListSolutionVersionsResponse;
import software.amazon.awssdk.services.personalize.model.ListSolutionsRequest;
import software.amazon.awssdk.services.personalize.model.ListSolutionsResponse;
import software.amazon.awssdk.services.personalize.model.RecipeSummary;
import software.amazon.awssdk.services.personalize.model.SolutionSummary;
import software.amazon.awssdk.services.personalize.model.SolutionVersionSummary;

/**
 *
 * @author Simon Schmid, KNIME GmbH, Konstanz, Germany
 */
public class AmazonPersonalizeUtils {

    /**
     * Interface for a function that waits for a certain event to happen.
     */
    public interface WaitFunction {
        /**
         * @return true, if the execution can be continued
         */
        boolean continueExecution();
    }

    /**
     * @param f the function which checks if the execution can be continued
     * @param millis the time to wait until another check is done with the wait function
     * @throws InterruptedException
     */
    public static void waitUntilActive(final WaitFunction f, final int millis) throws InterruptedException {
        while (true) {
            Thread.sleep(millis);
            if (f.continueExecution()) {
                break;
            }
        }
    }

    /**
     * @param personalize the amazon personalize client
     * @return all dataset groups
     */
    public static List<DatasetGroupSummary> listAllDatasetGroups(final PersonalizeClient personalize) {
        final var listDatasetGroupsRequest = ListDatasetGroupsRequest.builder()
                .maxResults(100).build();
        ListDatasetGroupsResponse listDatasetGroups = personalize.listDatasetGroups(listDatasetGroupsRequest);
        List<DatasetGroupSummary> datasetGroups = listDatasetGroups.datasetGroups();
        String nextToken;
        while ((nextToken = listDatasetGroups.nextToken()) != null) {
            listDatasetGroups = personalize.listDatasetGroups(ListDatasetGroupsRequest.builder()
                .nextToken(nextToken).build());
            datasetGroups.addAll(listDatasetGroups.datasetGroups());
        }
        return datasetGroups;
    }

    /**
     * @param personalize the amazon personalize client
     * @return all recipes
     */
    public static List<RecipeSummary> listAllRecipes(final PersonalizeClient personalize) {
        final var request = ListRecipesRequest.builder().maxResults(100).build();
        ListRecipesResponse result = personalize.listRecipes(request);
        List<RecipeSummary> list = result.recipes();
        String nextToken;
        while ((nextToken = result.nextToken()) != null) {
            result = personalize.listRecipes(ListRecipesRequest.builder()
                .nextToken(nextToken).build());
            list.addAll(result.recipes());
        }
        return list;
    }

    /**
     * @param personalize the amazon personalize client
     * @return all schemas
     */
    public static List<DatasetSchemaSummary> listAllSchemas(final PersonalizeClient personalize) {
        final var request = ListSchemasRequest.builder().maxResults(100).build();
        ListSchemasResponse result = personalize.listSchemas(request);
        List<DatasetSchemaSummary> list = result.schemas();
        String nextToken;
        while ((nextToken = result.nextToken()) != null) {
            result = personalize.listSchemas(ListSchemasRequest.builder()
                .nextToken(nextToken).build());
            list.addAll(result.schemas());
        }
        return list;
    }

    /**
     * @param personalize the amazon personalize client
     * @return all solution versions
     */
    public static List<SolutionVersionSummary> listAllSolutionVersions(final PersonalizeClient personalize) {
        final var request = ListSolutionVersionsRequest.builder()
                .maxResults(100).build();
        ListSolutionVersionsResponse result = personalize.listSolutionVersions(request);
        List<SolutionVersionSummary> list = result.solutionVersions();
        String nextToken;
        while ((nextToken = result.nextToken()) != null) {
            result = personalize.listSolutionVersions(ListSolutionVersionsRequest.builder()
                .nextToken(nextToken).build());
            list.addAll(result.solutionVersions());
        }
        return list;
    }

    /**
     * @param personalize the amazon personalize client
     * @return all solutions
     */
    public static List<SolutionSummary> listAllSolutions(final PersonalizeClient personalize) {
        final var request = ListSolutionsRequest.builder()
                .maxResults(100).build();
        ListSolutionsResponse result = personalize.listSolutions(request);
        List<SolutionSummary> list = result.solutions();
        String nextToken;
        while ((nextToken = result.nextToken()) != null) {
            result = personalize.listSolutions(ListSolutionsRequest.builder()
                .nextToken(nextToken).build());
            list.addAll(result.solutions());
        }
        return list;
    }

    /**
     * @param personalize the amazon personalize client
     * @return all campaigns
     */
    public static List<CampaignSummary> listAllCampaigns(final PersonalizeClient personalize) {
        final var request = ListCampaignsRequest.builder()
                .maxResults(100).build();
        ListCampaignsResponse result = personalize.listCampaigns(request);
        List<CampaignSummary> list = result.campaigns();
        String nextToken;
        while ((nextToken = result.nextToken()) != null) {
            result = personalize.listCampaigns(ListCampaignsRequest.builder()
                .nextToken(nextToken).build());
            list.addAll(result.campaigns());
        }
        return list;
    }

    /**
     * @param connectionInformation the cloud connection information
     * @return all dataset groups
     * @throws Exception if an exception occurs during establishing the connection to Amazon
     */
    public static List<Role> listAllRoles(final CloudConnectionInformation connectionInformation) throws Exception {
        try (final var connection = new AmazonIdentityManagementConnection(connectionInformation)) {
            final IamClient client = connection.getClient();
            final var listRolesRequest = ListRolesRequest.builder()
                    .maxItems(1000).build();

            ListRolesResponse listRoles = client.listRoles(listRolesRequest);
            List<Role> roles = listRoles.roles();
            String nextToken;
            while ((nextToken = listRoles.marker()) != null) {
                listRoles = client.listRoles(ListRolesRequest.builder().marker(nextToken).build());
                roles.addAll(listRoles.roles());
            }
            return roles;
        }
    }

    /**
     * Enumeration of relevant states a job on Amazon Personalize can have.
     *
     * @author Simon Schmid, KNIME GmbH, Konstanz, Germany
     */
    public enum Status {
            /** If the job succeeded and is active. */
            ACTIVE("ACTIVE"),
            /** If the job failed. */
            CREATED_FAILED("CREATE FAILED");

        private final String m_status;

        /**
         *
         */
        private Status(final String status) {
            m_status = status;
        }

        /**
         * @return the status
         */
        public String getStatus() {
            return m_status;
        }
    }
}
