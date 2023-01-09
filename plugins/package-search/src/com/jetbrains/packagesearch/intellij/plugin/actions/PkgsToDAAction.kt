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

package com.jetbrains.packagesearch.intellij.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.externalSystem.dependency.analyzer.DAArtifact
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import org.jetbrains.annotations.Nls

internal class PkgsToDAAction : AnAction(
    /* text = */ PackageSearchBundle.message("packagesearch.quickfix.packagesearch.action.da"),
    /* description = */ PackageSearchBundle.message("packagesearch.quickfix.packagesearch.action.da.description"),
    /* icon = */ null
) {

    companion object {

        val PACKAGES_LIST_PANEL_DATA_KEY: DataKey<PackageModel.Installed?> = DataKey.create("packageSearch.packagesListPanelDataKey")
    }

    override fun isDumbAware() = true

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val data = e.getData(PACKAGES_LIST_PANEL_DATA_KEY)
        with(e.presentation) {
            val hasData = data != null && data.usagesByModule.values.flatten()
                .any { it.module.buildSystemType.dependencyAnalyzerKey != null }
            isEnabledAndVisible = hasData
            if (hasData) {
                text = PackageSearchBundle.message(
                    "packagesearch.quickfix.packagesearch.action.da.withIdentifier",
                    data!!.identifier.rawValue
                )
                description = PackageSearchBundle.message(
                    "packagesearch.quickfix.packagesearch.action.da.description.withParam",
                    data.identifier.rawValue
                )
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val data = e.getData(PACKAGES_LIST_PANEL_DATA_KEY) ?: return
        val allUsages = data.usagesByModule.values.flatten()
        val dependencyAnalyzerSupportedUsages = allUsages
            .filter { it.module.buildSystemType.dependencyAnalyzerKey != null }
        if (dependencyAnalyzerSupportedUsages.size > 1) {
            val defaultActionGroup = buildActionGroup {
                dependencyAnalyzerSupportedUsages.forEach { usage ->
                    add(usage.module.name) {
                        navigateToDA(
                          group = data.groupId,
                          artifact = data.artifactId,
                          version = usage.getDeclaredVersionOrFallback().versionName,
                          module = usage.module.nativeModule,
                          systemId = usage.module.buildSystemType.dependencyAnalyzerKey!!
                        )
                    }
                }
            }

            @Suppress("DialogTitleCapitalization")
            val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                PackageSearchBundle.message("packagesearch.quickfix.packagesearch.action.da.selectModule", data.identifier.rawValue),
                defaultActionGroup,
                e.dataContext,
                JBPopupFactory.ActionSelectionAid.NUMBERING,
                true,
                null,
                10
            )

            e.project?.let { popup.showCenteredInCurrentWindow(it) } ?: popup.showInBestPositionFor(e.dataContext)
        } else {
            val usage = allUsages.single()
            navigateToDA(
              group = data.groupId,
              artifact = data.artifactId,
              version = usage.getDeclaredVersionOrFallback().versionName,
              module = usage.module.nativeModule,
              systemId = usage.module.buildSystemType.dependencyAnalyzerKey!!
            )
        }
    }

    @Suppress("HardCodedStringLiteral")
    private fun navigateToDA(group: String, artifact: String, version: String, module: Module, systemId: ProjectSystemId) =
        DependencyAnalyzerManager.getInstance(module.project)
            .getOrCreate(systemId)
            .setSelectedDependency(
                module = module,
                data = DAArtifact(group, artifact, version)
            )
}

fun buildActionGroup(builder: DefaultActionGroup.() -> Unit) = DefaultActionGroup().apply(builder)

private fun DefaultActionGroup.add(@Nls title: String, actionPerformed: (AnActionEvent) -> Unit) =
    add(object : AnAction(title) {
        override fun actionPerformed(e: AnActionEvent) = actionPerformed(e)
    })
