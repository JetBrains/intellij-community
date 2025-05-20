// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class PoetryPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager(project)

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit> {
    if (installRequest !is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications) {
      return Result.failure(UnsupportedOperationException("Poetry supports installing only  packages from repositories"))
    }

    val packageSpecifications = installRequest.specifications
    return addPackages(packageSpecifications, options)
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): Result<Unit> {
    return addPackages(specifications.map { it.copy(versionSpec = null) }, emptyList())
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): Result<Unit> {
    return poetryUninstallPackage(sdk, *pythonPackages).map { }
  }


  override suspend fun loadPackagesCommand(): Result<List<PythonPackage>> {
    val (installed, _) = poetryListPackages(sdk).getOrElse {
      return Result.failure(it)
    }

    val packages = installed.map {
      PythonPackage(it.name, it.version, false)
    }

    return Result.success(packages)
  }

  override suspend fun loadOutdatedPackagesCommand(): Result<List<PythonOutdatedPackage>> = poetryShowOutdated(sdk).map {
    it.values.toList()
  }

  private suspend fun addPackages(
    packageSpecifications: List<PythonRepositoryPackageSpecification>,
    options: List<String>,
  ): Result<Unit> {
    val specifications = packageSpecifications.map {
      it.getPackageWithVersionInPoetryFormat()
    }

    return poetryInstallPackage(sdk, specifications, options).map { }
  }


  private fun PythonRepositoryPackageSpecification.getPackageWithVersionInPoetryFormat(): String {
    return versionSpec?.let { "$name@${it.presentableText}" } ?: name
  }
}

/**
 * Parses the output of `poetry show` into a list of packages.
 */

@TestOnly
fun parsePoetryShowOutdatedTest(input: String): Map<String, PythonOutdatedPackage> {
  return parsePoetryShowOutdated(input)
}