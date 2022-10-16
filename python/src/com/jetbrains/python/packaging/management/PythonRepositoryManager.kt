// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PythonRepositoryManager(val project: Project, val sdk: Sdk) {

  abstract val repositories: List<PyPackageRepository>

  abstract fun allPackages(): List<String>

  abstract fun packagesFromRepository(repository: PyPackageRepository): List<String>
  suspend fun addRepository(repository: PyPackageRepository) { TODO() }
  suspend fun removeRepository(repository: PyPackageRepository) { TODO() }
  abstract suspend fun getPackageDetails(pkg: PythonPackageSpecification): PythonPackageDetails

  abstract suspend fun refreshCashes()

  abstract suspend fun initCaches()

  internal abstract fun buildPackageDetails(rawInfo: String, spec: PythonPackageSpecification): PythonPackageDetails
}