// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import org.jetbrains.annotations.TestOnly

@TestOnly
class TestPackageManagerProvider : PythonPackageManagerProvider {
  private var packageNames: List<String> = emptyList()
  private var packageDetails: PythonPackageDetails? = null
  private var packageInstalled: List<PythonPackage> = emptyList()
  private var packageVersions = mapOf<String, List<String>>()

  fun withPackageNames(packageNames: List<String>): TestPackageManagerProvider {
    this.packageNames = packageNames
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails): TestPackageManagerProvider {
    this.packageDetails = details
    return this
  }

  fun withPackageInstalled(vararg packages: PythonPackage): TestPackageManagerProvider {
    this.packageInstalled = packages.toList()
    return this
  }

  fun withRepoPackagesVersions(versions: Map<String, List<String>>): TestPackageManagerProvider {
    this.packageVersions = versions
    return this
  }


  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager {
    return TestPythonPackageManager(project, sdk)
      .withPackageNames(packageNames)
      .withPackageDetails(packageDetails)
      .withPackageInstalled(packageInstalled)
      .withRepoPackagesVersions(packageVersions)
  }
}