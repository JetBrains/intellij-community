// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
interface PythonRepositoryManager {
  val project: Project
  val repositories: List<PyPackageRepository>

  fun allPackages(): Set<String>

  suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails>
  suspend fun getLatestVersion(packageName: String, repository: PyPackageRepository?): PyPackageVersion?
  suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>?

  @Throws(IOException::class)
  suspend fun refreshCaches()

  @Throws(IOException::class)
  suspend fun initCaches()

  fun searchPackages(query: String, repository: PyPackageRepository): List<String> {
    val normalizedQuery = normalizePackageName(query)
    return repository.getPackages().filter { StringUtil.containsIgnoreCase(normalizePackageName(it), normalizedQuery) }
  }


  fun hasPackageSnapshot(packageName: String): Boolean {
    return repositories.any { packageName in it.getPackages() }
  }

  suspend fun findPackageSpecification(
    requirement: PyRequirement,
    repository: PyPackageRepository? = null,
  ): PythonRepositoryPackageSpecification?


  fun searchPackages(query: String): Map<PyPackageRepository, List<String>> {
    return repositories.associateWith { searchPackages(query, it) }
  }

  @ApiStatus.Internal
  suspend fun matchRequirement(requirement: PyRequirement): Boolean {
    val versions = getVersions(requirement.name, null) ?: return false
    return versions.any { version ->
      requirement.versionSpecs.any { spec -> spec.matches(version) }
    }
  }
}
