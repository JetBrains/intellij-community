// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
internal interface PythonRepositoryManager {
  val project: Project
  val repositories: List<PyPackageRepository>

  suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails>
  suspend fun getLatestVersion(packageName: String, repository: PyPackageRepository?): PyPackageVersion?
  suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>?

  @Throws(IOException::class)
  suspend fun refreshCaches()

  @Throws(IOException::class)
  suspend fun initCaches()

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
}
