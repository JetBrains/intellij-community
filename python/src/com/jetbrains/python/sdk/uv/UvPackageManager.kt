// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.cancelOnDispose
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManager.Companion.PackageManagerErrorMessage
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.resolvePyProjectToml
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class UvPackageManager(project: Project, sdk: Sdk, uvExecutionContextDeferred: Deferred<UvExecutionContext<*>>) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)
  private lateinit var uvLowLevel: PyResult<UvLowLevel<*>>
  private val uvExecutionContextDeferred = uvExecutionContextDeferred.also { it.cancelOnDispose(this) }

  private suspend fun <T> withUv(action: suspend (UvLowLevel<*>) -> PyResult<T>): PyResult<T> {
    if (!this::uvLowLevel.isInitialized) {
      uvLowLevel = uvExecutionContextDeferred.await().createUvCli()
    }

    return when (val uvResult = uvLowLevel) {
      is Result.Success -> action(uvResult.result)
      is Result.Failure -> uvResult
    }
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>, module: Module?): PyResult<Unit> {
    return withUv { uv ->
      if (module != null) {
        uv.addDependency(installRequest, emptyList(), PyWorkspaceMember(module.name))
      }
      else if (sdk.uvUsePackageManagement) {
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

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember?): PyResult<Unit> {
    return withUv { uv ->
      if (pythonPackages.isEmpty()) return@withUv PyResult.success(Unit)

      if (workspaceMember != null) {
        val packageNames = pythonPackages.map { PyPackageName.from(it) }
        uninstallDeclaredPackages(uv, packageNames, workspaceMember).getOr { return@withUv it }
        uv.lock().getOr { return@withUv it }
        uv.sync().getOr { return@withUv it }
        return@withUv PyResult.success(Unit)
      }

      val (standalonePackages, declaredPackages) = categorizePackages(pythonPackages).getOr {
        return@withUv it
      }

      uninstallStandalonePackages(uv, standalonePackages).getOr { return@withUv it }
      uninstallDeclaredPackages(uv, declaredPackages, null).getOr { return@withUv it }

      PyResult.success(Unit)
    }
  }

  override suspend fun extractDependencies(): PyResult<List<PythonPackage>> {
    return listAllTopLevelPackages()
  }

  private suspend fun listAllTopLevelPackages(): PyResult<List<PythonPackage>> {
    val modules = readAction { project.modules }
    val allPackages = mutableSetOf<PythonPackage>()
    var lastFailure: PyResult<List<PythonPackage>>? = null
    for (module in modules) {
      val result = withUv { uv -> uv.listTopLevelPackages(module) }
      when (result) {
        is Result.Success -> allPackages.addAll(result.result)
        is Result.Failure -> lastFailure = result
      }
    }
    if (allPackages.isEmpty() && lastFailure != null) {
      return lastFailure
    }
    return PyResult.success(allPackages.distinctBy { it.name })
  }

  /**
   * Categorizes packages into standalone packages and pyproject.toml declared packages.
   */
  private suspend fun categorizePackages(packages: Array<out String>): PyResult<Pair<List<PyPackageName>, List<PyPackageName>>> {
    val dependencyNames = listAllTopLevelPackages().getOr {
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
  private suspend fun uninstallStandalonePackages(uv: UvLowLevel<*>, packages: List<PyPackageName>): PyResult<Unit> {
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
  private suspend fun uninstallDeclaredPackages(uv: UvLowLevel<*>, packages: List<PyPackageName>, workspaceMember: PyWorkspaceMember?): PyResult<Unit> {
    return if (packages.isNotEmpty()) {
      uv.removeDependencies(packages.map { it.name }.toTypedArray(), workspaceMember)
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

  // TODO PY-87712 Double check for remotes
  override fun getDependencyFile(): VirtualFile? {
    val uvWorkingDirectory = runBlockingMaybeCancellable { uvExecutionContextDeferred.await().workingDir }
    return resolvePyProjectToml(uvWorkingDirectory)
  }

  override suspend fun addDependencyImpl(requirement: PyRequirement): Boolean = withContext(Dispatchers.IO) {
    val specification = repositoryManager.findPackageSpecification(requirement) ?: return@withContext false
    
    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(specification))

    withUv { uv ->
        uv.addDependency(request, emptyList())
    }.getOr { return@withContext false }

    return@withContext true
  }
}

class UvPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? {
    if (!sdk.isUv) {
      return null
    }

    val uvExecutionContext = sdk.getUvExecutionContextAsync(PyPackageCoroutine.getScope(project), project) ?: return null
    return UvPackageManager(project, sdk, uvExecutionContext)
  }
}
