// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.project.Project
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.management.PythonRepositoryManager.PythonRepositoryIOError
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PythonRepositoryManagerBase

internal class PixiTestPythonRepositoryManager(override val project: Project) : PythonRepositoryManagerBase() {
  override val allRepositories = listOf(PyPiPackageRepository)

  override suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails> =
    PyResult.success(PythonSimplePackageDetails(packageName, emptyList(), repository ?: PyPiPackageRepository))

  override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String> = emptyList()

  override suspend fun initCaches(): Result<Unit, PythonRepositoryIOError> = Result.Success(Unit)

  override suspend fun refreshCaches(): Result<Unit, PythonRepositoryIOError> = Result.Success(Unit)
}
