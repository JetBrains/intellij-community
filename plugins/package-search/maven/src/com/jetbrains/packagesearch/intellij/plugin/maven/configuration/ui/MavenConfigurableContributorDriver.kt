/**
 * ****************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 * ****************************************************************************
 */

package com.jetbrains.packagesearch.intellij.plugin.maven.configuration.ui

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AnalyticsAwareConfigurableContributorDriver
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.PackageSearchMavenConfiguration

internal class MavenConfigurableContributorDriver(project: Project) : AnalyticsAwareConfigurableContributorDriver {

    private val configuration = PackageSearchMavenConfiguration.getInstance(project)

    override fun contributeUserInterface(panel: Panel) {
        panel.apply {
            group(PackageSearchBundle.message("packagesearch.configuration.maven.title")) {
                row(PackageSearchBundle.message("packagesearch.configuration.maven.scopes.default")) {
                    textField()
                        .horizontalAlign(HorizontalAlign.FILL)
                        .resizableColumn()
                        .comment(
                            "${PackageSearchBundle.message("packagesearch.configuration.maven.scopes")} " +
                                configuration.getMavenScopes().joinToString()
                        )
                        .bindText(configuration::defaultMavenScope)
                }
            }
        }
    }

    override fun provideApplyEventAnalyticsData(): List<EventPair<*>> = listOf(
        PackageSearchEventsLogger.preferencesDefaultMavenScopeChangedField
            .with(configuration.defaultMavenScope != PackageSearchMavenConfiguration.DEFAULT_MAVEN_SCOPE)
    )
}
