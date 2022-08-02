package org.jetbrains.idea.packagesearch.api

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.idea.packagesearch.HashingAlgorithm
import org.jetbrains.packagesearch.api.statistics.ApiStatisticsResponse
import org.jetbrains.packagesearch.api.v2.ApiPackageResponse
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiRepositoriesResponse
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage
import java.util.concurrent.CompletableFuture

interface AsyncPackageSearchProvider {
  val myScope: CoroutineScope

  fun packagesByQuery(
    searchQuery: String,
    onlyStable: Boolean = false,
    onlyMpp: Boolean = false,
    repositoryIds: List<String> = emptyList()
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>>

  fun suggestPackages(
    groupId: String?,
    artifactId: String?,
    onlyMpp: Boolean = false,
    repositoryIds: List<String> = emptyList()
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>>

  fun packagesByRange(
    range: List<String>
  ): CompletableFuture<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>>

  fun packageByHash(
    hash: String,
    hashingAlgorithm: HashingAlgorithm
  ): CompletableFuture<ApiPackageResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>>

  fun packageById(id: String): CompletableFuture<ApiPackageResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>>

  fun readmeByPackageId(id: String): CompletableFuture<String>

  fun statistics(): CompletableFuture<ApiStatisticsResponse>

  fun repositories(): CompletableFuture<ApiRepositoriesResponse>
}