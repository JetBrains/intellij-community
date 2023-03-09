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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import org.jetbrains.idea.packagesearch.DefaultPackageServiceConfig
import org.jetbrains.idea.packagesearch.HashingAlgorithm
import org.jetbrains.idea.packagesearch.PackageSearchServiceConfig
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage
import java.util.concurrent.CompletableFuture

@Service(PROJECT)
internal class LifecycleScope : CoroutineScope, Disposable {
  override val coroutineContext = SupervisorJob() + AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()
  override fun dispose() = cancel()
}

fun AsyncPackageSearchApiClient(
  project: Project,
  config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>(),
  engine: HttpClientEngine? = null
) = AsyncPackageSearchApiClient(project.service<LifecycleScope>(), config, engine)

class AsyncPackageSearchApiClient(
  private val scope: CoroutineScope,
  config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>(),
  engine: HttpClientEngine? = null
) {

  private val myClient = PackageSearchApiClient(config, engine)

  fun packagesByQuery(
    searchQuery: String,
    onlyStable: Boolean,
    onlyMpp: Boolean,
    repositoryIds: List<String>
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> =
    scope.future { myClient.packagesByQuery(searchQuery, onlyStable, onlyMpp, repositoryIds) }

  fun suggestPackages(
    groupId: String?,
    artifactId: String?,
    onlyMpp: Boolean,
    repositoryIds: List<String>
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> =
    scope.future { myClient.suggestPackages(groupId, artifactId, onlyMpp, repositoryIds) }


  fun packagesByRange(range: List<String>) =
    scope.future { myClient.packagesByRange(range) }

  fun packageByHash(hash: String, hashingAlgorithm: HashingAlgorithm) =
    scope.future { myClient.packageByHash(hash, hashingAlgorithm) }

  fun packageById(id: String) =
    scope.future { myClient.packageById(id) }

  fun readmeByPackageId(id: String) =
    scope.future { myClient.readmeByPackageId(id) }

  fun statistics() =
    scope.future { myClient.statistics() }

  fun repositories() =
    scope.future { myClient.repositories() }
}