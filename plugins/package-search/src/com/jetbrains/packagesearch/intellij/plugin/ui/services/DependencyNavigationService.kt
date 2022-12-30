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

package com.jetbrains.packagesearch.intellij.plugin.ui.services

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.dependencytoolwindow.DependencyToolWindowFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackagesListPanelProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.util.pkgsUiStateModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class DependencyNavigationService(private val project: Project) {

    /**
     * Open the Dependency toolwindows at the selected [module] if found and searches for the first
     * coordinates that matches the ones of [coordinates].
     * @param module The native [Module] into which look for the dependency.
     * @param coordinates The [UnifiedCoordinates] to search, the first result will be used.
     */
    fun navigateToDependency(module: Module, coordinates: UnifiedCoordinates): NavigationResult {
        val entry = project.packageSearchProjectService.dependenciesByModuleStateFlow.value
            .entries.find { (k, _) -> k.nativeModule == module }
        return when {
            entry != null -> {
                val (projectModule, installedDependencies) = entry
                val isFound = installedDependencies.find { it.dependency.coordinates == coordinates }
                val moduleModel = project.packageSearchProjectService.moduleModelsStateFlow.value
                    .find { it.projectModule == projectModule }
                when {
                    isFound != null && moduleModel != null -> onSuccess(moduleModel, isFound.dependency)
                    else -> NavigationResult.CoordinatesNotFound(module, coordinates)
                }
            }
            else -> NavigationResult.ModuleNotSupported(module)
        }
    }

    /**
     * Open the Dependency toolwindows at the selected [module] if found and searches for the exact
     * dependency that matches the ones of [dependency].
     * @param module The native [Module] into which look for the dependency.
     * @param dependency The [UnifiedDependency] to search.
     */
    fun navigateToDependency(module: Module, dependency: UnifiedDependency): NavigationResult {
        val entry = project.packageSearchProjectService.dependenciesByModuleStateFlow.value
            .entries.find { (k, _) -> k.nativeModule == module }

        return when {
            entry != null -> {
                val (projectModule, installedDependencies) = entry
                val moduleModel = project.packageSearchProjectService.moduleModelsStateFlow.value
                    .find { it.projectModule == projectModule }
                when {
                    installedDependencies.any { it.dependency == dependency } && moduleModel != null -> onSuccess(moduleModel, dependency)
                    else -> NavigationResult.DependencyNotFound(module, dependency)
                }
            }
            else -> NavigationResult.ModuleNotSupported(module)
        }
    }

    private fun onSuccess(moduleModel: ModuleModel, dependency: UnifiedDependency): NavigationResult.Success {
        project.lifecycleScope.launch(Dispatchers.EDT) {
            DependencyToolWindowFactory.activateToolWindow(project, PackagesListPanelProvider) {
                project.pkgsUiStateModifier.setTargetModules(TargetModules.from(moduleModel))
                project.pkgsUiStateModifier.setDependency(dependency)
            }
        }
        return NavigationResult.Success
    }

    companion object {

        @JvmStatic
        fun getInstance(project: Project): DependencyNavigationService =
            project.service()
    }
}

sealed class NavigationResult {
    object Success : NavigationResult()
    data class ModuleNotSupported(val module: Module) : NavigationResult()
    data class DependencyNotFound(val module: Module, val dependency: UnifiedDependency) : NavigationResult()
    data class CoordinatesNotFound(val module: Module, val coordinates: UnifiedCoordinates) : NavigationResult()
}
