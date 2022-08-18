/*
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
 */

package org.jetbrains.idea.packagesearch.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.idea.packagesearch.DefaultPackageServiceConfig
import org.jetbrains.idea.packagesearch.HashingAlgorithm
import org.jetbrains.idea.packagesearch.PackageSearchServiceConfig
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class AsyncPackageSearchProvider(
  private val config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>()
) : DependencySearchProvider, Disposable {

  private val myService = PackageSearchProvider(config)

  private val myScope =
    CoroutineScope(SupervisorJob() + AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher())

  override fun dispose() {
    myScope.cancel("Disposing ${this::class.qualifiedName}")
  }

  @Deprecated("Use V2 PKGS API: [AsyncPackageSearchProvider#packagesByQuery]")
  @ScheduledForRemoval
  override fun fulltextSearch(searchString: String, consumer: Consumer<RepositoryArtifactData>) =
    myService.fulltextSearch(searchString, consumer)

  @Deprecated("Use V2 PKGS API: [AsyncPackageSearchProvider#suggestPackages]")
  @ScheduledForRemoval
  override fun suggestPrefix(groupId: String?, artifactId: String?, consumer: Consumer<RepositoryArtifactData>) =
    myService.suggestPrefix(groupId, artifactId, consumer)

  override fun isLocal(): Boolean = false

  fun packagesByQuery(
    searchQuery: String,
    onlyStable: Boolean,
    onlyMpp: Boolean,
    repositoryIds: List<String>
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> =
    myScope.future { myService.packagesByQuery(searchQuery, onlyStable, onlyMpp, repositoryIds) }

  fun suggestPackages(
    groupId: String?,
    artifactId: String?,
    onlyMpp: Boolean,
    repositoryIds: List<String>
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> =
    myScope.future { myService.suggestPackages(groupId, artifactId, onlyMpp, repositoryIds) }


  fun packagesByRange(range: List<String>) =
    myScope.future { myService.packagesByRange(range) }

  fun packageByHash(hash: String, hashingAlgorithm: HashingAlgorithm) =
    myScope.future { myService.packageByHash(hash, hashingAlgorithm) }

  fun packageById(id: String) =
    myScope.future { myService.packageById(id) }

  fun readmeByPackageId(id: String) =
    myScope.future { myService.readmeByPackageId(id) }

  fun statistics() =
    myScope.future { myService.statistics() }

  fun repositories() =
    myScope.future { myService.repositories() }
}