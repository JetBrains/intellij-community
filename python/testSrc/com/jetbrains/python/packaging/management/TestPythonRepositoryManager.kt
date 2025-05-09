// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.TestOnly

@TestOnly
internal class TestPythonRepositoryManager(
  override val project: Project,
) : PythonRepositoryManager {

  private var packageNames: Set<String> = emptySet()
  private var packageDetails: PythonPackageDetails? = null

  fun withPackageNames(packageNames: List<String>): TestPythonRepositoryManager {
    this.packageNames = packageNames.toSet()
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails?): TestPythonRepositoryManager {
    this.packageDetails = details
    return this
  }

  override fun searchPackages(query: String, repository: PyPackageRepository): List<String> {
    TODO("Not yet implemented")
  }

  override fun searchPackages(query: String): Map<PyPackageRepository, List<String>> {
    TODO("Not yet implemented")
  }

  override val repositories: List<PyPackageRepository>
    get() = listOf(TestPackageRepository(packageNames))

  override fun allPackages(): Set<String> {
    return packageNames
  }

  override suspend fun getPackageDetails(pkg: PythonRepositoryPackageSpecification): PyResult<PythonPackageDetails> {
    return PyResult.success(checkNotNull(packageDetails))
  }

  override suspend fun getLatestVersion(spec: PythonRepositoryPackageSpecification): PyPackageVersion? {
    TODO("Not yet implemented")
  }

  override suspend fun refreshCaches() {
  }

  override suspend fun initCaches() {
  }
}

internal class TestPackageRepository(private val packages: Set<String>) : PyPackageRepository("test repository", null, null) {
  override fun getPackages(): Set<String> {
    return packages
  }
}