// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName
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
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)

  override suspend fun syncCommand(): PyResult<Unit> {
    return runPoetryWithSdk(sdk, "install").mapSuccess { }
  }

  suspend fun lockProject(): PyResult<Unit> {
    runPoetryWithSdk(sdk, "lock").getOr {
      return it
    }
    return reloadPackages().mapSuccess { }
  }


  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    if (installRequest !is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications) {
      return PyResult.localizedError(PyBundle.message("python.sdk.poetry.supports.installing.only.packages.from.repositories"))
    }

    val packageSpecifications = installRequest.specifications
    return addPackages(packageSpecifications, options)
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    return addPackages(specifications.map { it.copy(versionSpec = null) }, emptyList())
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    if (pythonPackages.isEmpty()) return PyResult.success(Unit)

    val (standalonePackages, declaredPackages) = categorizePackages(pythonPackages)

    uninstallDeclaredPackages(declaredPackages).getOr { return it }
    uninstallStandalonePackages(standalonePackages).getOr { return it }

    return PyResult.success(Unit)
  }

  /**
   * Categorizes packages into standalone packages and pyproject.toml declared packages.
   */
  private fun categorizePackages(packages: Array<out String>): Pair<List<NormalizedPythonPackageName>, List<NormalizedPythonPackageName>> {
    val dependencyNames = dependencies.map { it.name }.toSet()
    return packages
      .map { NormalizedPythonPackageName.from(it) }
      .partition { it.name !in dependencyNames }
  }

  /**
   * Uninstalls packages using pip through Poetry.
   */
  private suspend fun uninstallStandalonePackages(packages: List<NormalizedPythonPackageName>): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      poetryUninstallPackage(
        sdk = sdk,
        packages = packages.map { it.name }.toTypedArray()
      ).mapSuccess { }
    }
    else {
      PyResult.success(Unit)
    }
  }

  /**
   * Removes packages declared in pyproject.toml using Poetry.
   */
  private suspend fun uninstallDeclaredPackages(packages: List<NormalizedPythonPackageName>): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      poetryRemovePackage(
        sdk = sdk,
        packages = packages.map { it.name }.toTypedArray()
      ).mapSuccess { }
    }
    else {
      PyResult.success(Unit)
    }
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val (installed, _) = poetryListPackages(sdk).getOr { return it }

    val packages = installed.map {
      PythonPackage(it.name, it.version, false)
    }

    return PyResult.success(packages)
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> = poetryShowOutdated(sdk).mapSuccess {
    it.values.toList()
  }

  private suspend fun addPackages(
    packageSpecifications: List<PythonRepositoryPackageSpecification>,
    options: List<String>,
  ): PyResult<Unit> {
    val specifications = packageSpecifications.map {
      it.getPackageWithVersionInPoetryFormat()
    }

    return poetryInstallPackage(sdk, specifications, options).mapSuccess { }
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