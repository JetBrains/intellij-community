// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.TestOnly

@TestOnly
class TestPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {

  override var installedPackages: List<PythonPackage> = DEFAULT_PACKAGES.toMutableList()

  private var packageNames: List<String> = emptyList()
  private var packageDetails: PythonPackageDetails? = null

  override val repositoryManager: PythonRepositoryManager
    get() = TestPythonRepositoryManager(project, sdk).withPackageNames(packageNames).withPackageDetails(packageDetails)

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<Unit> {
    return if (repositoryManager.allPackages().contains(specification.name)) {
      installedPackages += PythonPackage(specification.name, specification.versionSpecs.orEmpty(), false)
      Result.success(Unit)
    } else {
      Result.failure(Exception(PACKAGE_INSTALL_FAILURE_MESSAGE))
    }
  }

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<Unit> {
    return Result.success(Unit)
  }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit> {
    val packageToRemove = findPackageByName(pkg.name)
    return if (packageToRemove != null) {
      installedPackages -= packageToRemove
      Result.success(Unit)
    } else {
      Result.failure(Exception(PACKAGE_UNINSTALL_FAILURE_MESSAGE))
    }
  }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> {
    return Result.success(installedPackages)
  }

  private fun findPackageByName(name: String): PythonPackage? {
    return installedPackages.find { it.name == name }
  }

  fun withPackageNames(packageNames: List<String>): TestPythonPackageManager {
    this.packageNames = packageNames
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails?): TestPythonPackageManager {
    this.packageDetails = details
    return this
  }

  companion object {
    private val DEFAULT_PACKAGES = listOf(
      PythonPackage(PIP_PACKAGE, EMPTY_STRING, false),
      PythonPackage(SETUP_TOOLS_PACKAGE, EMPTY_STRING, false)
    )

    private const val PIP_PACKAGE = "pip"
    private const val SETUP_TOOLS_PACKAGE = "setuptools"
    private const val PACKAGE_INSTALL_FAILURE_MESSAGE = "Failed to install package"
    private const val PACKAGE_UNINSTALL_FAILURE_MESSAGE = "No such package found"
    private const val EMPTY_STRING = ""
  }
}

@TestOnly
class TestPythonPackageManagerService(): PythonPackageManagerService {

  override fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    return TestPythonPackageManager(project, sdk)
  }

  override fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge {
    return PythonPackageManagementServiceBridge(project, sdk)
  }

  override fun getServiceScope(): CoroutineScope {
    return CoroutineScope(Job())
  }
}

@TestOnly
class TestPackageManagerProvider : PythonPackageManagerProvider {
  private var packageNames: List<String> = emptyList()
  private var packageDetails: PythonPackageDetails? = null

  fun withPackageNames(packageNames: List<String>): TestPackageManagerProvider {
    this.packageNames = packageNames
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails): TestPackageManagerProvider {
    this.packageDetails = details
    return this
  }

  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager {
    return TestPythonPackageManager(project, sdk).withPackageNames(packageNames).withPackageDetails(packageDetails)
  }
}