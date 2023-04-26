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

import com.intellij.dependencytoolwindow.DependencyToolWindowOpener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.jetbrains.packagesearch.PackageSearchIcons
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackagesListPanelProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.util.pkgsUiStateModifier

class AddDependencyAction : AnAction(
    PackageSearchBundle.message("packagesearch.actions.addDependency.text"),
    PackageSearchBundle.message("packagesearch.actions.addDependency.description"),
    PackageSearchIcons.Artifact
) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isEnabled(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val modules = project.packageSearchProjectService.packageSearchModulesStateFlow.value
        if (modules.isEmpty()) return

        val selectedModule = findSelectedModule(e, modules) ?: return

        DependencyToolWindowOpener.activateToolWindow(project, PackagesListPanelProvider) {
            project.pkgsUiStateModifier.setTargetModules(TargetModules.One(selectedModule))
        }
    }

    private fun findSelectedModule(e: AnActionEvent, modules: List<PackageSearchModule>): PackageSearchModule? {
        val project = e.project ?: return null
        val file = obtainSelectedProjectDirIfSingle(e)?.virtualFile ?: return null
        val selectedModule = runReadAction { ModuleUtilCore.findModuleForFile(file, project) } ?: return null

        // Sanity check that the module we got actually exists
        ModuleManager.getInstance(project).findModuleByName(selectedModule.name)
            ?: return null

        return modules.firstOrNull { module -> module.nativeModule == selectedModule }
    }

    private fun obtainSelectedProjectDirIfSingle(e: AnActionEvent): PsiDirectory? {
        val dataContext = e.dataContext
        val ideView = LangDataKeys.IDE_VIEW.getData(dataContext)
        val selectedDirectories = ideView?.directories ?: return null

        if (selectedDirectories.size != 1) return null

        return selectedDirectories.first()
    }

    private fun isEnabled(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val file = e.getData(CommonDataKeys.EDITOR)?.virtualFile ?: return false

        val module = findSelectedModule(e, project.packageSearchProjectService.packageSearchModulesStateFlow.value)
        return module?.buildFile?.path?.equals(file.path) ?: false
    }
}
