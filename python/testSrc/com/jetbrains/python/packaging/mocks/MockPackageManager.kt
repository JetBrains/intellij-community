// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.mocks

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import org.jetbrains.annotations.TestOnly

@TestOnly
class MockPythonPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {

  override var installedPackages: List<PythonPackage> = DEFAULT_PACKAGES.toMutableList()

  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project, sdk)

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<String> {
    return if (repositoryManager.allPackages().contains(specification.name)) {
      installedPackages += PythonPackage(specification.name, specification.versionSpecs.orEmpty(), false)
      Result.success(PACKAGE_INSTALLED_MESSAGE)
    } else {
      Result.failure(Exception(PACKAGE_INSTALL_FAILURE_MESSAGE))
    }
  }

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<String> {
    return Result.success(PACKAGE_UPDATED_MESSAGE)
  }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<String> {
    val packageToRemove = findPackageByName(pkg.name)
    return if (packageToRemove != null) {
      installedPackages -= packageToRemove
      Result.success(PACKAGE_UNINSTALLED_MESSAGE)
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

  companion object {
    private val DEFAULT_PACKAGES = listOf(
      PythonPackage(PIP_PACKAGE, EMPTY_STRING, false),
      PythonPackage(SETUP_TOOLS_PACKAGE, EMPTY_STRING, false)
    )

    private const val PIP_PACKAGE = "pip"
    private const val SETUP_TOOLS_PACKAGE = "setuptools"
    private const val PACKAGE_INSTALLED_MESSAGE = "Successfully installed package"
    private const val PACKAGE_INSTALL_FAILURE_MESSAGE = "Failed to install package"
    private const val PACKAGE_UPDATED_MESSAGE = "Successfully updated package"
    private const val PACKAGE_UNINSTALLED_MESSAGE = "Successfully uninstalled package"
    private const val PACKAGE_UNINSTALL_FAILURE_MESSAGE = "No such package found"
    private const val EMPTY_STRING = ""
  }
}