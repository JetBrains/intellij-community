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
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

abstract class AbstractAsyncProjectModuleOperationProvider : AsyncProjectModuleOperationProvider {

    override fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>> =
        module.lifecycleScope.future { AbstractCoroutineProjectModuleOperationProvider.addDependencyToModule(operationMetadata, module) }

    override fun removeDependencyFromModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>> =
        module.lifecycleScope.future { AbstractCoroutineProjectModuleOperationProvider.removeDependencyFromModule(operationMetadata, module) }

    override fun updateDependencyInModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>> =
        module.lifecycleScope.future { AbstractCoroutineProjectModuleOperationProvider.updateDependencyInModule(operationMetadata, module) }

    override fun declaredDependenciesInModule(module: ProjectModule): CompletableFuture<Collection<DeclaredDependency>> =
        module.lifecycleScope.future { AbstractCoroutineProjectModuleOperationProvider.declaredDependenciesInModule(module) }

    override fun resolvedDependenciesInModule(module: ProjectModule, scopes: Set<String>): CompletableFuture<Collection<UnifiedDependency>> =
        CompletableFuture.completedFuture(emptyList())

    override fun addRepositoryToModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>> =
        module.lifecycleScope.future { AbstractCoroutineProjectModuleOperationProvider.addRepositoryToModule(repository, module) }

    override fun removeRepositoryFromModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): CompletableFuture<Collection<OperationFailure<out OperationItem>>> =
        module.lifecycleScope.future { AbstractCoroutineProjectModuleOperationProvider.removeRepositoryFromModule(repository, module) }

    override fun listRepositoriesInModule(module: ProjectModule): CompletableFuture<Collection<UnifiedDependencyRepository>> =
        module.lifecycleScope.future { AbstractCoroutineProjectModuleOperationProvider.listRepositoriesInModule(module) }
}