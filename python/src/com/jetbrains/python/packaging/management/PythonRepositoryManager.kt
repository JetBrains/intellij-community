// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Experimental
interface PythonRepositoryManager {
  @Deprecated("Don't use sdk from here")
  val sdk: Sdk
  val project: Project
  val repositories: List<PyPackageRepository>

  fun allPackages(): Set<String>
  fun searchPackages(query: String): Map<PyPackageRepository, List<String>>
  fun searchPackages(query: String, repository: PyPackageRepository): List<String>

  suspend fun getPackageDetails(pkg: PythonPackageSpecification): PythonPackageDetails
  suspend fun getLatestVersion(spec: PythonPackageSpecification): PyPackageVersion?
  fun buildPackageDetails(rawInfo: String?, spec: PythonPackageSpecification): PythonPackageDetails

  @Throws(IOException::class)
  suspend fun refreshCaches()
  @Throws(IOException::class)
  suspend fun initCaches()
}
