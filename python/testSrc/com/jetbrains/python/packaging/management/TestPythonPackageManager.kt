// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import org.jetbrains.annotations.TestOnly

@TestOnly
class TestPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  private var packageNames: List<String> = emptyList()
  private var packageDetails: PythonPackageDetails? = null

  override val installedPackages: List<PythonPackage>
    get() = TODO("Not yet implemented")
  override val repositoryManager: PythonRepositoryManager
    get() = TestPythonRepositoryManager(project, sdk).withPackageNames(packageNames).withPackageDetails(packageDetails)

  fun withPackageNames(packageNames: List<String>): TestPythonPackageManager {
    this.packageNames = packageNames
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails?): TestPythonPackageManager {
    this.packageDetails = details
    return this
  }

  override suspend fun installPackage(specification: PythonPackageSpecification): Result<List<PythonPackage>> {
    TODO("Not yet implemented")
  }

  override suspend fun updatePackage(specification: PythonPackageSpecification): Result<List<PythonPackage>> {
    TODO("Not yet implemented")
  }

  override suspend fun uninstallPackage(pkg: PythonPackage): Result<List<PythonPackage>> {
    TODO("Not yet implemented")
  }

  override suspend fun reloadPackages(): Result<List<PythonPackage>> {
    TODO("Not yet implemented")
  }
}