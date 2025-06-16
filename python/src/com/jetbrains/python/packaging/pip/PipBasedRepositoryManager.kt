// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.service
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCache
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.time.Duration

@ApiStatus.Experimental
internal abstract class PipBasedRepositoryManager() : PythonRepositoryManager {

  override val repositories: List<PyPackageRepository>
    get() = listOf(PyPIPackageRepository) + service<PythonSimpleRepositoryCache>().repositories

  private val packageDetailsCache = Caffeine.newBuilder()
    .maximumSize(10)
    .expireAfterWrite(Duration.ofHours(1))
    .build<PythonRepositoryPackageSpecification, PyResult<PythonPackageDetails>> {
      it.repository.buildPackageDetails(it.name)
    }

  private val latestVersions = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofDays(1))
    .build<PythonRepositoryPackageSpecification, PyPackageVersion?> { spec ->
      val details = packageDetailsCache.get(spec).getOrNull()
      details?.availableVersions?.firstOrNull()?.let {
        PyPackageVersionNormalizer.normalize(it)
      }
    }

  @Throws(IOException::class)
  override suspend fun initCaches() {
    service<PypiPackageCache>().reloadCache().orThrow()

    val repositoryService = service<PyPackageRepositories>()
    val repositoryCache = service<PythonSimpleRepositoryCache>()
    if (repositoryService.repositories.isNotEmpty() && repositoryCache.isEmpty()) {
      repositoryCache.refresh()
    }
  }

  @Throws(IOException::class)
  override suspend fun refreshCaches() {
    service<PypiPackageCache>().reloadCache(force = true).orThrow()
    service<PythonSimpleRepositoryCache>().refresh()
  }

  override fun allPackages(): Set<String> =
    repositories.flatMap { it.getPackages() }.toSet()

  override suspend fun getPackageDetails(pkg: PythonRepositoryPackageSpecification): PyResult<PythonPackageDetails> {
    return packageDetailsCache[pkg]
  }

  override suspend fun getLatestVersion(spec: PythonRepositoryPackageSpecification): PyPackageVersion? {
    return latestVersions[spec]
  }

  override fun searchPackages(query: String, repository: PyPackageRepository): List<String> {
    val normalizedQuery = normalizePackageName(query)
    return repository.getPackages().filter { StringUtil.containsIgnoreCase(normalizePackageName(it), normalizedQuery) }
  }

  override fun searchPackages(query: String): Map<PyPackageRepository, List<String>> {
    return repositories.associateWith { searchPackages(query, it) }
  }
}