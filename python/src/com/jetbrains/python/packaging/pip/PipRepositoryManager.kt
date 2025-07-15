// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCache
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PythonRepositoryManagerBase
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.time.Duration

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class PipRepositoryManager(override val project: Project) : PythonRepositoryManagerBase() {
  override val repositories: List<PyPackageRepository>
    get() = listOf(PyPIPackageRepository) + service<PythonSimpleRepositoryCache>().repositories

  private val packageDetailsCache = Caffeine.newBuilder()
    .maximumSize(200)
    .expireAfterWrite(Duration.ofHours(1))
    .build<Pair<String, PyPackageRepository?>, PyResult<PythonPackageDetails>> { (packageName, repository) ->
      (repository ?: PyPIPackageRepository).buildPackageDetails(packageName)
    }

  init {
    if (!shouldBeInitInstantly())
      initializationJob.start()
  }


  override suspend fun getPackageDetails(spec: PythonRepositoryPackageSpecification): PyResult<PythonPackageDetails> {
    return packageDetailsCache.get(spec.name to spec.repository)
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

  override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>? {
    val details = packageDetailsCache.get(packageName to repository).getOrNull() ?: return null
    return details.availableVersions
  }

  companion object {
    fun getInstance(project: Project): PipRepositoryManager = project.service()
  }
}