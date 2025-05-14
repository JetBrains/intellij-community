// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.common.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.TestOnly

@TestOnly
class TestPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {

  override var installedPackages: List<PythonPackage> = DEFAULT_PACKAGES.toMutableList()

  private var packageNames: List<String> = emptyList()
  private var packageDetails: PythonPackageDetails? = null

  override val repositoryManager: PythonRepositoryManager
    get() = TestPythonRepositoryManager(project).withPackageNames(packageNames).withPackageDetails(packageDetails)

  override suspend fun loadOutdatedPackagesCommand(): Result<List<PythonOutdatedPackage>> {
    return Result.success(emptyList())
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit> {
    if (installRequest !is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecification) {
      return Result.failure(Exception("Test Manager supports only simple repository package specification"))
    }

    return if (repositoryManager.allPackages().contains(installRequest.specification.name)) {
      val version = installRequest.specification.versionSpec?.version.orEmpty()
      installedPackages += PythonPackage(installRequest.specification.name, version, false)
      Result.success(Unit)
    } else {
      Result.failure(Exception(PACKAGE_INSTALL_FAILURE_MESSAGE))
    }
  }

  override suspend fun updatePackageCommand(specification: PythonRepositoryPackageSpecification): Result<Unit> {
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

  fun withPackageInstalled(packages: List<PythonPackage>): TestPythonPackageManager {
    this.installedPackages = packages
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
class TestPythonPackageManagerService(val installedPackages: List<PythonPackage> = emptyList()): PythonPackageManagerService {

  override fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    installedPackages.ifEmpty {
      return TestPythonPackageManager(project, sdk)
    }

    return TestPythonPackageManager(project, sdk)
      .withPackageInstalled(installedPackages)
      .withPackageNames(installedPackages.map { it.name })
      .withPackageDetails(PythonSimplePackageDetails(installedPackages.first().name, listOf(installedPackages.first().version), TestPackageRepository(installedPackages.map { it.name }.toSet())))
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
  private var packageInstalled: List<PythonPackage> = emptyList()

  fun withPackageNames(packageNames: List<String>): TestPackageManagerProvider {
    this.packageNames = packageNames
    return this
  }

  fun withPackageDetails(details: PythonPackageDetails): TestPackageManagerProvider {
    this.packageDetails = details
    return this
  }

  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager {
    return TestPythonPackageManager(project, sdk).withPackageNames(packageNames).withPackageDetails(packageDetails).withPackageInstalled(packageInstalled)
  }
}