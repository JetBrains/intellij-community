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

package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.util.asCoroutine
import java.util.concurrent.CompletableFuture

interface AsyncProjectModuleOperationProvider {

    companion object {

        private val extensionPointName
            get() = ExtensionPointName.create<AsyncProjectModuleOperationProvider>("com.intellij.packagesearch.asyncProjectModuleOperationProvider")

        internal val extensions
            get() = extensionPointName.extensions.map { it.asCoroutine() }
    }

    /**
     * Checks if current implementation has support in the given [project] for the current [psiFile].
     * @return `true` if the [project] and [psiFile] are supported.
     */
    fun hasSupportFor(project: Project, psiFile: PsiFile?): Boolean

    /**
     * Checks if current implementation has support in the given [projectModuleType].
     * @return `true` if the [projectModuleType] is supported.
     */
    fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean

    /**
     * Adds a dependency to the given [module] using [operationMetadata].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>>

    /**
     * Removes a dependency from the given [module] using [operationMetadata].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun removeDependencyFromModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>>

    /**
     * Modify a dependency in the given [module] using [operationMetadata].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun updateDependencyInModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>>

    /**
     * Lists all dependencies declared in the given [module]. A declared dependency
     * have to be explicitly written in the build file.
     * @return A [Collection]<[UnifiedDependency]> found in the project.
     */
    fun declaredDependenciesInModule(
        module: ProjectModule
    ): CompletableFuture<Collection<DeclaredDependency>>

    /**
     * Lists all resolved dependencies in the given [module].
     * @return A [Collection]<[UnifiedDependency]> found in the project.
     */
    fun resolvedDependenciesInModule(
        module: ProjectModule,
        scopes: Set<String> = emptySet()
    ): CompletableFuture<Collection<UnifiedDependency>>

    /**
     * Adds the [repository] to the given [module].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun addRepositoryToModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>>

    /**
     * Removes the [repository] from the given [module].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun removeRepositoryFromModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>>

    /**
     * Lists all repositories in the given [module].
     * @return A [Collection]<[UnifiedDependencyRepository]> found the project.
     */
    fun listRepositoriesInModule(
        module: ProjectModule
    ): CompletableFuture<Collection<UnifiedDependencyRepository>>
}
