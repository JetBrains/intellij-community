// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.mapError
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.management.PythonRepositoryManager.PythonRepositoryIOError
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PythonRepositoryManagerBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class CondaRepositoryManger(override val project: Project, val sdk: Sdk) : PythonRepositoryManagerBase() {
  private val pipRepositoryManger = PipRepositoryManager.getInstance(project)

  override val builtInRepositories: List<PyPackageRepository>
    get() = listOf(CondaPackageRepository) + pipRepositoryManger.builtInRepositories

  override val repositories: List<PyPackageRepository>
    get() = listOf(CondaPackageRepository) + pipRepositoryManger.repositories

  private val condaPackageCache = service<CondaPackageCache>()

  init {
    if (!shouldBeInitInstantly())
      initializationJob.start()
  }

  override suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails> {
    waitForInit()
    val packageDetails = pipRepositoryManger.getPackageDetails(packageName, repository)
    return packageDetails
  }

  override suspend fun refreshCaches(): Result<Unit, PythonRepositoryIOError> {
    pipRepositoryManger
      .refreshCaches()
      .getOr { return it }

    return condaPackageCache
      .reloadCache(sdk, project, force = true)
      .mapError { PythonRepositoryIOError(it.message) }
  }

  override suspend fun initCaches(): Result<Unit, PythonRepositoryIOError> {
    pipRepositoryManger.waitForInit()

    return condaPackageCache
      .reloadCache(sdk, project)
      .mapError { PythonRepositoryIOError(it.message) }
  }

  override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>? {
    waitForInit()
    return when (repository) {
      null -> {
        val condaVersions = condaPackageCache[packageName]
        val pipVersions = pipRepositoryManger.getVersions(packageName, repository)
        if (condaVersions == null && pipVersions == null) return null
        val allVersions = (condaVersions ?: emptyList()) + (pipVersions ?: emptyList())
        allVersions.distinct()
      }
      is CondaPackageRepository -> condaPackageCache[packageName]
      else -> pipRepositoryManger.getVersions(packageName, repository)
    }
  }
}