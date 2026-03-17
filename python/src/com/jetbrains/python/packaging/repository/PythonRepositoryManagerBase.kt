// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.cancelOnDispose
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.PyPackagingSettings
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PythonRepositoryManagerBase : PythonRepositoryManager, Disposable.Default {
  protected val initializationJob: Job by lazy {
    PyPackageCoroutine.launch(project, NON_INTERACTIVE_ROOT_TRACE_CONTEXT, start = CoroutineStart.LAZY) {
      initCaches()
    }.also {
      it.cancelOnDispose(this)
    }
  }

  @ApiStatus.Internal
  suspend fun waitForInit() {
    if (shouldBeInitInstantly()) {
      initCaches()
    }
    else {
      initializationJob.join()
    }
  }


  override fun allPackages(): Set<String> {
    if (repositories.size == 1)
      return repositories.first().getPackages()

    val result = mutableSetOf<String>()
    for (repository in repositories) {
      result.addAll(repository.getPackages())
    }
    return result
  }

  override suspend fun getLatestVersion(packageName: String, repository: PyPackageRepository?): PyPackageVersion? {
    waitForInit()
    val versions = getVersions(packageName, repository) ?: return null
    val version = PyPackagingSettings.getInstance(project).selectLatestVersion(versions) ?: return null
    return PyPackageVersionNormalizer.normalize(version)
  }

  override suspend fun findPackageSpecification(requirement: PyRequirement, repository: PyPackageRepository?): PythonRepositoryPackageSpecification? {
    waitForInit()
    if (repository != null) {
      return repository.findPackageSpecification(requirement)
    }
    val found = repositories.firstNotNullOfOrNull { it.findPackageSpecification(requirement) }
    if (found == null) {
      thisLogger().debug("Package specification not found for $requirement. Tried repositories: ${
        repositories.joinToString(",") { "${it.name}: packages=${it.getPackages().size}" }
      }")
      return found
    }
    return found
  }


  //Some test on EDT so need to be inited on first create
  protected fun shouldBeInitInstantly(): Boolean = ApplicationManager.getApplication().isUnitTestMode
}