// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.repository.PyEmptyPackagePackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.TestOnly

@TestOnly
class TestPythonRepositoryManager(project: Project, sdk: Sdk) : PythonRepositoryManager(project, sdk) {

  private var packageNames: List<String> = emptyList()
  private var packageDetails: PythonPackageDetails? = null

  fun withPackageNames(packageNames: List<String>): TestPythonRepositoryManager {
    this.packageNames = packageNames
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails?): TestPythonRepositoryManager {
    this.packageDetails = details
    return this
  }

  override fun buildPackageDetails(rawInfo: String?, spec: PythonPackageSpecification): PythonPackageDetails {
    TODO("Not yet implemented")
  }

  override fun searchPackages(query: String, repository: PyPackageRepository): List<String> {
    TODO("Not yet implemented")
  }

  override fun searchPackages(query: String): Map<PyPackageRepository, List<String>> {
    TODO("Not yet implemented")
  }

  override val repositories: List<PyPackageRepository>
    get() = listOf(PyEmptyPackagePackageRepository)

  override fun allPackages(): List<String> {
    return packageNames
  }

  override fun packagesFromRepository(repository: PyPackageRepository): List<String> {
    return packageNames
  }

  override suspend fun getPackageDetails(pkg: PythonPackageSpecification): PythonPackageDetails {
    assert(packageDetails != null)
    return packageDetails!!
  }

  override suspend fun getLatestVersion(spec: PythonPackageSpecification): PyPackageVersion? {
    TODO("Not yet implemented")
  }

  override suspend fun refreshCashes() {
    TODO("Not yet implemented")
  }

  override suspend fun initCaches() {
    TODO("Not yet implemented")
  }
}