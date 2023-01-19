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
    fun navigateToDependency(module: Module, coordinates: UnifiedCoordinates) =
        project.packageSearchProjectService
            .installedDependenciesFlow
            .value
            .all
            .find { it.groupId == coordinates.groupId && it.artifactId == coordinates.artifactId }
            ?.usagesByModule
            ?.get(module)
            ?.find { it.declaredVersion.displayName == coordinates.version }
            ?.let { UnifiedDependency(coordinates, it.scope.displayName) }
            ?.let { onSuccess(module, it) }
            ?: NavigationResult.CoordinatesNotFound(module, coordinates)

    /**
     * Open the Dependency toolwindows at the selected [module] if found and searches for the exact
     * dependency that matches the ones of [dependency].
     * @param module The native [Module] into which look for the dependency.
     * @param dependency The [UnifiedDependency] to search.
     */
    fun navigateToDependency(module: Module, dependency: UnifiedDependency) =
        project.packageSearchProjectService
            .installedDependenciesFlow
            .value
            .all
            .find { it.groupId == dependency.coordinates.groupId && it.artifactId == dependency.coordinates.artifactId }
            ?.usagesByModule
            ?.get(module)
            ?.find { it.declaredVersion.displayName == dependency.coordinates.version && it.scope.displayName == dependency.scope }
            ?.let { onSuccess(module, dependency) }
            ?: NavigationResult.CoordinatesNotFound(module, dependency.coordinates)

    private fun onSuccess(module: Module, dependency: UnifiedDependency): NavigationResult {
        val pkgsModule = project.packageSearchProjectService.packageSearchModulesStateFlow
            .value
            .find { it.nativeModule == module }
        if (pkgsModule != null) {
            project.lifecycleScope.launch(Dispatchers.EDT) {
                DependencyToolWindowFactory.activateToolWindow(project, PackagesListPanelProvider) {
                    project.pkgsUiStateModifier.setTargetModules(TargetModules.from(pkgsModule))
                    project.pkgsUiStateModifier.setDependency(dependency)
                }
            }
            return NavigationResult.Success
        }
        else return NavigationResult.CoordinatesNotFound(module, dependency.coordinates)
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
