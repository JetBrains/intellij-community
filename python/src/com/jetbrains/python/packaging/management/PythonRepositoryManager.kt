// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
interface PythonRepositoryManager {
  val project: Project
  val repositories: List<PyPackageRepository>

  fun allPackages(): Set<String>

  suspend fun getPackageDetails(spec: PythonRepositoryPackageSpecification): PyResult<PythonPackageDetails>
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

  suspend fun findPackageSpecification(
    name: String,
    version: String? = null,
    relation: PyRequirementRelation = PyRequirementRelation.EQ,
    repository: PyPackageRepository? = null,
  ): PythonRepositoryPackageSpecification?

  fun searchPackages(query: String): Map<PyPackageRepository, List<String>> {
    return repositories.associateWith { searchPackages(query, it) }
  }
}
