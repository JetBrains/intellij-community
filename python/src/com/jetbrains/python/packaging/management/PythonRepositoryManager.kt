// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PythonRepositoryManager(val project: Project, val sdk: Sdk) {
  abstract val repositories: List<PyPackageRepository>

  abstract fun allPackages(): List<String>

  abstract fun packagesFromRepository(repository: PyPackageRepository): List<String>
  abstract suspend fun getPackageDetails(pkg: PythonPackageSpecification): PythonPackageDetails
  abstract suspend fun getLatestVersion(spec: PythonPackageSpecification): PyPackageVersion?

  abstract suspend fun refreshCashes()

  abstract suspend fun initCaches()

  abstract fun buildPackageDetails(rawInfo: String?, spec: PythonPackageSpecification): PythonPackageDetails

  abstract fun searchPackages(query: String, repository: PyPackageRepository): List<String>
  abstract fun searchPackages(query: String): Map<PyPackageRepository, List<String>>
}