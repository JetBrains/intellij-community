// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import java.nio.file.Path

internal class UvPackageManager(project: Project, sdk: Sdk, private val uv: UvLowLevel) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    val result = if (sdk.uvUsePackageManagement) {
      uv.installPackage(installRequest, emptyList())
    }
    else {
      uv.addDependency(installRequest, emptyList())
    }
    return result
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val specsWithoutVersion = specifications.map { it.copy(requirement = pyRequirement(it.name, null)) }
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specsWithoutVersion)
    val result = installPackageCommand(request, emptyList())

    return result
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    if (pythonPackages.isEmpty()) return PyResult.success(Unit)

    val (standalonePackages, declaredPackages) = categorizePackages(pythonPackages)

    uninstallStandalonePackages(standalonePackages).getOr { return it }
    uninstallDeclaredPackages(declaredPackages).getOr { return it }

    return PyResult.success(Unit)
  }

  /**
   * Categorizes packages into standalone packages and pyproject.toml declared packages.
   */
  private fun categorizePackages(packages: Array<out String>): Pair<List<PyPackageName>, List<PyPackageName>> {
    val dependencyNames = dependencies.map { it.name }.toSet()
    return packages
      .map { PyPackageName.from(it) }
      .partition { it.name !in dependencyNames || sdk.uvUsePackageManagement }
  }

  /**
   * Uninstalls standalone packages using UV package manager.
   */
  private suspend fun uninstallStandalonePackages(packages: List<PyPackageName>): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      uv.uninstallPackages(packages.map { it.name }.toTypedArray())
    }
    else {
      PyResult.success(Unit)
    }
  }

  /**
   * Removes declared dependencies using UV package manager.
   */
  private suspend fun uninstallDeclaredPackages(packages: List<PyPackageName>): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      uv.removeDependencies(packages.map { it.name }.toTypedArray())
    }
    else {
      PyResult.success(Unit)
    }
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    return uv.listPackages()
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    return uv.listOutdatedPackages()
  }

  override suspend fun syncCommand(): PyResult<Unit> {
    return uv.sync().mapSuccess { }
  }

  suspend fun lock(): PyResult<Unit> {
    uv.lock().getOr {
      return it
    }
    return reloadPackages().mapSuccess { }
  }
}

class UvPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? {
    if (!sdk.isUv) {
      return null
    }

    val uvWorkingDirectory = (sdk.sdkAdditionalData as UvSdkAdditionalData).uvWorkingDirectory ?: Path.of(project.basePath!!)
    val uv = createUvLowLevel(uvWorkingDirectory, createUvCli())
    return UvPackageManager(project, sdk, uv)
  }
}