// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import org.jetbrains.annotations.TestOnly

@TestOnly
internal class TestPythonRepositoryManager(
  override val project: Project,
) : PythonRepositoryManager {

  private var packageNames: Set<String> = emptySet()
  private var packageDetails: PythonPackageDetails? = null

  override suspend fun findPackageSpecification(name: String, version: String?, relation: PyRequirementRelation, repository: PyPackageRepository?): PythonRepositoryPackageSpecification {
    return PythonRepositoryPackageSpecification(repository
                                                ?: PyPIPackageRepository, name, version?.let { pyRequirementVersionSpec(relation, it) })
  }

  fun withPackageNames(packageNames: List<String>): TestPythonRepositoryManager {
    this.packageNames = packageNames.toSet()
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails?): TestPythonRepositoryManager {
    this.packageDetails = details
    return this
  }


  override val repositories: List<PyPackageRepository>
    get() = listOf(TestPackageRepository(packageNames))

  override fun allPackages(): Set<String> {
    return packageNames
  }

  override suspend fun getPackageDetails(spec: PythonRepositoryPackageSpecification): PyResult<PythonPackageDetails> {
    return PyResult.success(checkNotNull(packageDetails))
  }


  override suspend fun refreshCaches() {
  }

  override suspend fun initCaches() {
  }

  override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String> {
    return packageDetails?.availableVersions?.toList().orEmpty()
  }

  override suspend fun getLatestVersion(packageName: String, repository: PyPackageRepository?): PyPackageVersion {
    TODO("Not yet implemented")
  }
}

internal class TestPackageRepository(private val packages: Set<String>) : PyPackageRepository("test repository", null, null) {
  override fun getPackages(): Set<String> {
    return packages
  }
}