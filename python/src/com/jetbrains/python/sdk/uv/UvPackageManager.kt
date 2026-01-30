// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.cancelOnDispose
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManager.Companion.PackageManagerErrorMessage
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.nio.file.Path

internal class UvPackageManager(project: Project, sdk: Sdk, uvLowLevelDeferred: Deferred<PyResult<UvLowLevel>>) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)
  private val uvLowLevel = uvLowLevelDeferred.also { it.cancelOnDispose(this) }

  private suspend fun <T> withUv(action: suspend (UvLowLevel) -> PyResult<T>): PyResult<T> {
    return when (val uvResult = uvLowLevel.await()) {
      is Result.Success -> action(uvResult.result)
      is Result.Failure -> uvResult
    }
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    return withUv { uv ->
      if (sdk.uvUsePackageManagement) {
        uv.installPackage(installRequest, emptyList())
      }
      else {
        uv.addDependency(installRequest, emptyList())
      }
    }
  }

  override suspend fun installPackageDetachedCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    return withUv { uv -> uv.installPackage(installRequest, emptyList()) }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val specsWithoutVersion = specifications.map { it.copy(requirement = pyRequirement(it.name, null)) }
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specsWithoutVersion)
    val result = installPackageCommand(request, emptyList())

    return result
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    return withUv { uv ->
      if (pythonPackages.isEmpty()) return@withUv PyResult.success(Unit)

      val (standalonePackages, declaredPackages) = categorizePackages(uv, pythonPackages).getOr {
        return@withUv it
      }

      uninstallStandalonePackages(uv, standalonePackages).getOr { return@withUv it }
      uninstallDeclaredPackages(uv, declaredPackages).getOr { return@withUv it }

      PyResult.success(Unit)
    }
  }

  override suspend fun extractDependencies(): PyResult<List<PythonPackage>> {
    return withUv { uv -> uv.listTopLevelPackages() }
  }

  /**
   * Categorizes packages into standalone packages and pyproject.toml declared packages.
   */
  private suspend fun categorizePackages(uv: UvLowLevel, packages: Array<out String>): PyResult<Pair<List<PyPackageName>, List<PyPackageName>>> {
    val dependencyNames = uv.listTopLevelPackages().getOr {
      return it
    }.map { it.name }

    val categorizedPackages = packages
      .map { PyPackageName.from(it) }
      .partition { it.name !in dependencyNames || sdk.uvUsePackageManagement }

    return PyResult.success(categorizedPackages)
  }

  /**
   * Uninstalls standalone packages using UV package manager.
   */
  private suspend fun uninstallStandalonePackages(uv: UvLowLevel, packages: List<PyPackageName>): PyResult<Unit> {
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
  private suspend fun uninstallDeclaredPackages(uv: UvLowLevel, packages: List<PyPackageName>): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      uv.removeDependencies(packages.map { it.name }.toTypedArray())
    }
    else {
      PyResult.success(Unit)
    }
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    return withUv { uv -> uv.listPackages() }
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    return withUv { uv -> uv.listOutdatedPackages() }
  }

  override suspend fun syncCommand(): PyResult<Unit> {
    return withUv { uv -> uv.sync().mapSuccess { } }
  }

  override fun syncErrorMessage(): PackageManagerErrorMessage =
    PackageManagerErrorMessage(
      message("python.uv.lockfile.out.of.sync"),
      message("python.uv.update.lock")
    )

  suspend fun lock(): PyResult<Unit> {
    return withUv { uv ->
      uv.lock().getOr {
        return@withUv it
      }
      reloadPackages().mapSuccess { }
    }
  }
}

class UvPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? {
    if (!sdk.isUv) {
      return null
    }

    val uvWorkingDirectory = (sdk.sdkAdditionalData as UvSdkAdditionalData).uvWorkingDirectory ?: Path.of(project.basePath!!)
    val uvLowLevel = PyPackageCoroutine.getScope(project).async(start = CoroutineStart.LAZY) {
      createUvCli().mapSuccess { createUvLowLevel(uvWorkingDirectory, it) }
    }
    return UvPackageManager(project, sdk, uvLowLevel)
  }
}
