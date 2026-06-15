// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCacheService
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PythonRepositoryManagerBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.time.Duration

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class PipRepositoryManager(override val project: Project) : PythonRepositoryManagerBase() {
  override val repositories: List<PyPackageRepository>
    get() = listOf(PyPiPackageRepository) + service<PythonSimpleRepositoryCacheService>().repositories

  private val packageDetailsCache = Caffeine.newBuilder()
    .maximumSize(200)
    .expireAfterWrite(Duration.ofHours(1))
    .build<Pair<String, PyPackageRepository>, PyResult<PythonPackageDetails>> { (packageName, repository) ->
      (repository).buildPackageDetails(packageName)
    }

  init {
    if (!shouldBeInitInstantly())
      initializationJob.start()
  }


  override suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?) = withContext(Dispatchers.IO) {
    packageDetailsCache.get(packageName to (repository ?: PyPiPackageRepository))
  }

  @Throws(IOException::class)
  override suspend fun initCaches() {
    service<PyPiPackageCache>().reloadCache().onFailure {
      thisLogger().warn("Failed to load PyPI packages cache. Error: $it")
    }.orThrow()

    val repositoryService = service<PyPackageRepositories>()
    val repositoryCache = service<PythonSimpleRepositoryCacheService>()
    if (repositoryService.repositories.isNotEmpty() && repositoryCache.isEmpty()) {
      repositoryCache.reloadAll().orThrow()
    }
    thisLogger().debug("Pip repository cache initialized with ${service<PyPiPackageCache>().size} packages" +
                       "and ${repositoryCache.repositories.size} repositories")
  }

  @Throws(IOException::class)
  override suspend fun refreshCaches() {
    service<PyPiPackageCache>().reloadCache(force = true).orThrow()
    service<PythonSimpleRepositoryCacheService>().reloadAll().orThrow()
  }

  override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>? {
    return getPackageDetails(packageName, repository).getOrNull()?.availableVersions
  }

  companion object {
    fun getInstance(project: Project): PipRepositoryManager = project.service()
  }
}