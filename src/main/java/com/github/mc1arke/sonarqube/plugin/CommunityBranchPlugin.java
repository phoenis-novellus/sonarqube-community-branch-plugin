/*
 * Copyright (C) 2020-2021 Michael Clarke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.github.mc1arke.sonarqube.plugin;

import com.github.mc1arke.sonarqube.plugin.almclient.DefaultLinkHeaderReader;
import com.github.mc1arke.sonarqube.plugin.almclient.azuredevops.DefaultAzureDevopsClientFactory;
import com.github.mc1arke.sonarqube.plugin.almclient.bitbucket.DefaultBitbucketClientFactory;
import com.github.mc1arke.sonarqube.plugin.almclient.github.DefaultGithubClientFactory;
import com.github.mc1arke.sonarqube.plugin.almclient.github.v3.RestApplicationAuthenticationProvider;
import com.github.mc1arke.sonarqube.plugin.almclient.gitlab.DefaultGitlabClientFactory;
import com.github.mc1arke.sonarqube.plugin.ce.CommunityReportAnalysisComponentProvider;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityBranchConfigurationLoader;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityBranchParamsValidator;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityProjectBranchesLoader;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityProjectPullRequestsLoader;
import com.github.mc1arke.sonarqube.plugin.scanner.ScannerPullRequestPropertySensor;
import com.github.mc1arke.sonarqube.plugin.server.CommunityBranchFeatureExtension;
import com.github.mc1arke.sonarqube.plugin.server.CommunityBranchSupportDelegate;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.validator.AzureDevopsValidator;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.validator.BitbucketValidator;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.validator.GithubValidator;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.validator.GitlabValidator;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.action.DeleteBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.action.SetAzureBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.action.SetBitbucketBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.action.SetBitbucketCloudBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.action.SetGithubBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.action.SetGitlabBindingAction;
import com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.action.ValidateBindingAction;
import org.sonar.api.CoreProperties;
import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;
import org.sonar.core.config.PurgeConstants;
import org.sonar.core.extension.CoreExtension;

/**
 * @author Michael Clarke
 */
public class CommunityBranchPlugin implements Plugin, CoreExtension {

    public static final String IMAGE_URL_BASE = "com.github.mc1arke.sonarqube.plugin.branch.image-url-base";
    public static final String AZURE_DEVOPS_API_VERSION = "com.github.mc1arke.sonarqube.plugin.branch.azure-devops-api-version";

    @Override
    public String getName() {
        return "Community Branch Plugin";
    }

    @Override
    public void load(CoreExtension.Context context) {
        if (SonarQubeSide.COMPUTE_ENGINE == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(CommunityReportAnalysisComponentProvider.class);
        } else if (SonarQubeSide.SERVER == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(
                    CommunityBranchFeatureExtension.class,
                    CommunityBranchSupportDelegate.class,
                    DeleteBindingAction.class,
                    SetGithubBindingAction.class,
                    SetAzureBindingAction.class,
                    SetBitbucketBindingAction.class,
                    SetBitbucketCloudBindingAction.class,
                    SetGitlabBindingAction.class,
                    ValidateBindingAction.class,
                    GithubValidator.class,
                    DefaultGithubClientFactory.class,
                    DefaultLinkHeaderReader.class,
                    RestApplicationAuthenticationProvider.class,
                    DefaultBitbucketClientFactory.class,
                    BitbucketValidator.class,
                    GitlabValidator.class,
                    DefaultGitlabClientFactory.class,
                    DefaultAzureDevopsClientFactory.class,
                    AzureDevopsValidator.class,

                /* org.sonar.db.purge.PurgeConfiguration uses the value for this property if it's configured, so it only
                needs to be specified here, but doesn't need any additional classes to perform the relevant purge/cleanup
                */
                    PropertyDefinition
                            .builder(PurgeConstants.DAYS_BEFORE_DELETING_INACTIVE_BRANCHES_AND_PRS)
                            .name("Number of days before purging inactive branches and pull requests")
                            .description(
                                    "Branches and pull requests are permanently deleted when there has been no analysis for the configured number of days.")
                            .category(CoreProperties.CATEGORY_HOUSEKEEPING)
                            .subCategory(CoreProperties.SUBCATEGORY_BRANCHES_AND_PULL_REQUESTS)
                            .defaultValue("30")
                            .type(PropertyType.INTEGER)
                            .index(1)
                            .build()
                    ,

                    PropertyDefinition
                            .builder(PurgeConstants.BRANCHES_TO_KEEP_WHEN_INACTIVE)
                            .name("Branches to keep when inactive")
                            .description("By default, branches and pull requests are automatically deleted when inactive. This setting allows you "
                                    + "to protect branches (but not pull requests) from this deletion. When a branch is created with a name that "
                                    + "matches any of the regular expressions on the list of values of this setting, the branch will not be deleted "
                                    + "automatically even when it becomes inactive. Example:"
                                    + "<ul><li>develop</li><li>release-.*</li></ul>")
                            .category(CoreProperties.CATEGORY_HOUSEKEEPING)
                            .subCategory(CoreProperties.SUBCATEGORY_BRANCHES_AND_PULL_REQUESTS)
                            .multiValues(true)
                            .defaultValue("master,develop,trunk")
                            .onQualifiers(Qualifiers.PROJECT)
                            .index(2)
                            .build()

            );

        }

        if (SonarQubeSide.COMPUTE_ENGINE == context.getRuntime().getSonarQubeSide() ||
                SonarQubeSide.SERVER == context.getRuntime().getSonarQubeSide()) {

            context.addExtensions(
                    PropertyDefinition.builder(AZURE_DEVOPS_API_VERSION)
                            .category(CoreProperties.CATEGORY_GENERAL)
                            .subCategory(CoreProperties.SUBCATEGORY_GENERAL)
                            .defaultValue("6.0")
                            .onQualifiers(Qualifiers.APP)
                            .name("Azure DevOps API Version")
                            .description("API Version to submit with any Azure DevOps API request")
                            .type(PropertyType.STRING)
                            .build(),

                    PropertyDefinition.builder(IMAGE_URL_BASE)
                            .category(CoreProperties.CATEGORY_GENERAL)
                            .subCategory(CoreProperties.SUBCATEGORY_GENERAL)
                            .onQualifiers(Qualifiers.APP)
                            .name("Images base URL")
                            .description("Base URL used to load the images for the PR comments (please use this only if images are not displayed properly).")
                            .type(PropertyType.STRING)
                            .build());

        }
    }

    @Override
    public void define(Plugin.Context context) {
        if (SonarQubeSide.SCANNER == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(
                    CommunityProjectBranchesLoader.class,
                    CommunityProjectPullRequestsLoader.class,
                    CommunityBranchConfigurationLoader.class,
                    CommunityBranchParamsValidator.class,
                    ScannerPullRequestPropertySensor.class);
        }
    }
}
