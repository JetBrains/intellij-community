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

class AsyncPackageSearchApiClient(
  private val config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>()
): Disposable {

  private val myClient = PackageSearchApiClient(config)

  private val myScope =
    CoroutineScope(SupervisorJob() + AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher())

  override fun dispose() {
    myScope.cancel("Disposing ${this::class.qualifiedName}")
  }

  fun packagesByQuery(
    searchQuery: String,
    onlyStable: Boolean,
    onlyMpp: Boolean,
    repositoryIds: List<String>
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> =
    myScope.future { myClient.packagesByQuery(searchQuery, onlyStable, onlyMpp, repositoryIds) }

  fun suggestPackages(
    groupId: String?,
    artifactId: String?,
    onlyMpp: Boolean,
    repositoryIds: List<String>
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> =
    myScope.future { myClient.suggestPackages(groupId, artifactId, onlyMpp, repositoryIds) }


  fun packagesByRange(range: List<String>) =
    myScope.future { myClient.packagesByRange(range) }

  fun packageByHash(hash: String, hashingAlgorithm: HashingAlgorithm) =
    myScope.future { myClient.packageByHash(hash, hashingAlgorithm) }

  fun packageById(id: String) =
    myScope.future { myClient.packageById(id) }

  fun readmeByPackageId(id: String) =
    myScope.future { myClient.readmeByPackageId(id) }

  fun statistics() =
    myScope.future { myClient.statistics() }

  fun repositories() =
    myScope.future { myClient.repositories() }
}