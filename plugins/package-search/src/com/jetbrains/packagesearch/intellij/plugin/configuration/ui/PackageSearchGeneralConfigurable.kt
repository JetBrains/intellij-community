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

package com.jetbrains.packagesearch.intellij.plugin.configuration.ui

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AnalyticsAwareConfigurableContributorDriver
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributor
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger

class PackageSearchGeneralConfigurable(project: Project) : BoundSearchableConfigurable(
    displayName = PackageSearchBundle.message("packagesearch.configuration.title"),
    helpTopic = "",
    _id = "preferences.packagesearch.PackageSearchGeneralConfigurable"
) {

    private val extensions = ConfigurableContributor.extensionsForProject(project)
        .sortedBy { it.javaClass.simpleName }
        .map { it.createDriver() }

    private val configuration = PackageSearchGeneralConfiguration.getInstance(project)

    override fun createPanel() = panel {
        row {
            checkBox(PackageSearchBundle.message("packagesearch.configuration.automatically.add.repositories"))
                .bindSelected(configuration::autoAddMissingRepositories)
        }

        // Extensions
        extensions.forEach {
            it.contributeUserInterface(this)
        }
    }

    override fun apply() {
        super.apply() // TODO this is not applying bound stuff in child configurables for whatever reason

        val analyticsFields = mutableSetOf<EventPair<*>>()
        for (contributor in extensions) {
            if (contributor is AnalyticsAwareConfigurableContributorDriver) {
                analyticsFields.addAll(contributor.provideApplyEventAnalyticsData())
            }
        }

        analyticsFields += PackageSearchEventsLogger.preferencesAutoAddRepositoriesField
            .with(configuration.autoAddMissingRepositories)
        PackageSearchEventsLogger.logPreferencesChanged(*analyticsFields.toTypedArray())
    }
}
