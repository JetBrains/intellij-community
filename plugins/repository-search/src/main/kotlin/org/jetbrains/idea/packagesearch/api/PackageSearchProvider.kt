package org.jetbrains.idea.packagesearch.api

import org.jetbrains.idea.packagesearch.HashingAlgorithm
import org.jetbrains.packagesearch.api.statistics.ApiStatisticsResponse
import org.jetbrains.packagesearch.api.v2.ApiPackageResponse
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiRepositoriesResponse
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage

interface PackageSearchProvider {
  suspend fun packagesByQuery(
    searchQuery: String,
    onlyStable: Boolean = false,
    onlyMpp: Boolean = false,
    repositoryIds: List<String> = emptyList()
  ): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>

  suspend fun suggestPackages(
    groupId: String?,
    artifactId: String?,
    onlyMpp: Boolean = false,
    repositoryIds: List<String> = emptyList()
  ): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>

  suspend fun packagesByRange(range: List<String>): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>

  suspend fun packageByHash(
    hash: String,
    hashingAlgorithm: HashingAlgorithm
  ): ApiPackageResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>

  suspend fun packageById(id: String): ApiPackageResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>

  suspend fun readmeByPackageId(id: String): String

  suspend fun statistics(): ApiStatisticsResponse

  suspend fun repositories(): ApiRepositoriesResponse
}