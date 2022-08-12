/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.ui

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.ui.StringValuesListPanel
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AnalyticsAwareConfigurableContributorDriver
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfiguration
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfigurationDefaults

internal class GradleConfigurableContributorDriver(project: Project) : AnalyticsAwareConfigurableContributorDriver {

    private val configuration = PackageSearchGradleConfiguration.getInstance(project)

    override fun contributeUserInterface(panel: Panel) {
        panel.apply {
            group(PackageSearchBundle.message("packagesearch.configuration.gradle.title")) {
                row {
                    val listPanel = StringValuesListPanel(configuration.gradleScopes.toList())

                    cell(listPanel.component)
                        .horizontalAlign(HorizontalAlign.FILL)
                        .resizableColumn()
                        .label(PackageSearchBundle.message("packagesearch.configuration.gradle.configurations"), LabelPosition.TOP)
                        .bind(
                            componentGet = { listPanel.values.toMutableSet() },
                            componentSet = { _, scopes -> listPanel.values = scopes.toList() },
                            prop = configuration::gradleScopes.toMutableProperty()
                        )
                }.bottomGap(BottomGap.MEDIUM)

                row {
                    checkBox(PackageSearchBundle.message("packagesearch.configuration.update.scopes.on.usage"))
                        .horizontalAlign(HorizontalAlign.FILL)
                        .resizableColumn()
                        .bindSelected(configuration::updateScopesOnUsage)
                }.bottomGap(BottomGap.MEDIUM)

                row(PackageSearchBundle.message("packagesearch.configuration.gradle.configurations.default")) {
                    textField()
                        .horizontalAlign(HorizontalAlign.FILL)
                        .resizableColumn()
                        .bindText(configuration::defaultGradleScope)
                }
            }
        }
    }

    override fun provideApplyEventAnalyticsData(): List<EventPair<*>> {
        val hasChangedDefaultScope =
            configuration.defaultGradleScope != PackageSearchGradleConfigurationDefaults.GradleDefaultScope

        return listOf(
            PackageSearchEventsLogger.preferencesGradleScopeCountField.with(configuration.gradleScopes.size),
            PackageSearchEventsLogger.preferencesUpdateScopesOnUsageField.with(configuration.updateScopesOnUsage),
            PackageSearchEventsLogger.preferencesDefaultGradleScopeChangedField.with(hasChangedDefaultScope),
        )
    }
}
