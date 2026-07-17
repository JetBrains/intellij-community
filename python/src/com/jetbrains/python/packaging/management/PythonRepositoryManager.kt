// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue

internal interface PythonRepositoryManager {
  val project: Project
  val repositories: List<PyPackageRepository>

  /**
   * Built-in repositories that are always shown in the repository settings (e.g. PyPI, Conda).
   * These are non-removable and added by the package manager itself, not by the user.
   */
  val builtInRepositories: List<PyPackageRepository> get() = DEFAULT_BUILT_IN_REPOSITORIES

  suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails>
  suspend fun getLatestVersion(packageName: String, repository: PyPackageRepository?): PyPackageVersion?
  suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>?

  @CheckReturnValue
  suspend fun refreshCaches(): Result<Unit, PythonRepositoryIOError>

  @CheckReturnValue
  suspend fun initCaches(): Result<Unit, PythonRepositoryIOError>

  /**
   * Suspend until any deferred repository initialisation has finished. Default is a no-op;
   * managers with asynchronous warm-up override this.
   */
  @ApiStatus.Internal
  suspend fun awaitReady() {}

  @RequiresBackgroundThread
  fun searchPackages(repository: PyPackageRepository, needle: String, pageSize: Int = 100): PythonPackageSearchResult {
    val normalizedNeedle = PyPackageName.normalizePackageName(needle)
    return repository.search(normalizedNeedle, pageSize)
  }

  @RequiresBackgroundThread
  fun searchPackages(needle: String, pageSize: Int = 100): Map<PyPackageRepository, PythonPackageSearchResult> {
    return repositories.associateWith { searchPackages(it, needle, pageSize) }
  }

  @RequiresBackgroundThread
  fun hasPackageSnapshot(packageName: String): Boolean {
    return repositories.any { it.hasPackage(packageName) }
  }

  suspend fun findPackageSpecification(
    requirement: PyRequirement,
    repository: PyPackageRepository? = null,
  ): PythonRepositoryPackageSpecification?

  @ApiStatus.Internal
  suspend fun matchRequirement(requirement: PyRequirement): Boolean {
    val versions = getVersions(requirement.name, null) ?: return false
    return versions.any { version ->
      requirement.versionSpecs.any { spec -> spec.matches(version) }
    }
  }

  data class PythonRepositoryIOError(val message: String)

  /**
   * Search-only projection of a [PythonRepositoryManager] that guarantees the manager is fully
   * initialised. Obtained via [PythonRepositoryManager.getSearchApi]; short-lived — do not cache.
   */
  @ApiStatus.Internal
  class PythonRepositorySearchApi internal constructor(private val manager: PythonRepositoryManager) {
    val repositories: List<PyPackageRepository> get() = manager.repositories

    @RequiresBackgroundThread
    fun searchPackages(query: String, pageSize: Int = 100): Map<PyPackageRepository, PythonPackageSearchResult> =
      manager.searchPackages(query, pageSize)

    @RequiresBackgroundThread
    fun searchPackages(repository: PyPackageRepository, needle: String, pageSize: Int = 100): PythonPackageSearchResult =
      manager.searchPackages(repository, needle, pageSize)
  }

  companion object {
    val DEFAULT_BUILT_IN_REPOSITORIES: List<PyPackageRepository> = listOf(PyPiPackageRepository)
  }
}

/**
 * Returns a search-ready handle, suspending until any deferred repository initialisation has
 * finished. Prefer this over the direct [PythonRepositoryManager.searchPackages] calls when
 * readiness is required — the type system then enforces that a caller has waited, so it can't
 * accidentally search against a half-initialised manager.
 */
@ApiStatus.Internal
internal suspend fun PythonRepositoryManager.getSearchApi(): PythonRepositoryManager.PythonRepositorySearchApi {
  awaitReady()
  return PythonRepositoryManager.PythonRepositorySearchApi(this)
}
