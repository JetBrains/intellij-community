// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.cancelOnDispose
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PythonRepositoryManagerBase() : PythonRepositoryManager, Disposable.Default {
  protected val initializationJob: Job by lazy {
    PyPackageCoroutine.launch(project, start = CoroutineStart.LAZY) {
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


  override fun allPackages(): Set<String> = repositories.flatMap { it.getPackages() }.toSet()

  override suspend fun getLatestVersion(packageName: String, repository: PyPackageRepository?): PyPackageVersion? {
    waitForInit()
    val version = getVersions(packageName, repository)?.firstOrNull() ?: return null
    return PyPackageVersionNormalizer.normalize(version)
  }

  override suspend fun findPackageSpecification(
    name: String,
    version: String?,
    relation: PyRequirementRelation,
    repository: PyPackageRepository?,
  ): PythonRepositoryPackageSpecification? {
    waitForInit()
    if (repository != null) {
      return repository.findPackageSpecification(name, version, relation)
    }
    return repositories.firstNotNullOfOrNull { it.findPackageSpecification(name, version, relation) }
  }


  //Some test on EDT so need to be inited on first create
  protected fun shouldBeInitInstantly(): Boolean = ApplicationManager.getApplication().isUnitTestMode
}