// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.conda.environmentYml.CondaEnvironmentYmlManager
import com.jetbrains.python.packaging.dependencies.PythonDependenciesManager
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerEngine
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipPackageManagerEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class CondaPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = CondaRepositoryManger(project, sdk).also {
    Disposer.register(this, it)
  }

  private val condaPackageEngine = CondaPackageManagerEngine(sdk)
  private val pipPackageEngine = PipPackageManagerEngine(project, sdk)

  override fun getDependencyManager(): PythonDependenciesManager {
    return CondaEnvironmentYmlManager.getInstance(project, sdk)
  }

  override suspend fun syncCommand(): PyResult<Unit> {
    val requirementsFile = getDependencyManager().getDependenciesFile()
                           ?: return PyResult.localizedError(PyBundle.message("python.sdk.conda.requirements.file.not.found"))
    return updateEnv(requirementsFile)
  }

  private suspend fun updateEnv(envFile: VirtualFile): PyResult<Unit> {
    condaPackageEngine.updateFromEnvironmentFile(envFile).onFailure {
      return PyResult.failure(it)
    }.getOrThrow()

    return reloadPackages().mapSuccess { }
  }


  suspend fun exportEnv(envFile: VirtualFile): PyResult<Unit> {
    val envText = condaPackageEngine.exportToEnvironmentFile().onFailure {
      return PyResult.failure(it)
    }.getOrThrow()

    writeAction {
      envFile.writeText(envText)
    }

    return PyResult.success(Unit)
  }


  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> = condaPackageEngine.loadPackagesCommand()

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> = coroutineScope {
    val condaOutdated = async {
      condaPackageEngine.loadOutdatedPackagesCommand()
    }
    val pipOutdated = async {
      pipPackageEngine.loadOutdatedPackagesCommand()
    }

    val condaPackages = condaOutdated.await().getOr {
      return@coroutineScope it
    }
    val pipPackages = pipOutdated.await().getOr {
      return@coroutineScope it
    }

    val onlyPipOutdated = pipPackages.filter { outdatedPackage ->
      val pythonPackage = installedPackages.firstOrNull { it.name == outdatedPackage.name } ?: return@filter false
      pythonPackage !is CondaPackage || pythonPackage.installedWithPip
    }
    PyResult.success(condaPackages + onlyPipOutdated)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> = when (installRequest) {
    is PythonPackageInstallRequest.ByLocation -> pipPackageEngine.installPackageCommand(installRequest, options)
    is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> installSeveralPackages(installRequest.specifications, options)
  }

  private suspend fun installSeveralPackages(specifications: List<PythonRepositoryPackageSpecification>, options: List<String>) =
    performOperation(specifications) { manager, specs ->
      val managerRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specs)
      manager.installPackageCommand(managerRequest, options)
    }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> =
    performOperation(specifications.toList()) { manager, specs ->
      manager.updatePackageCommand(*specs.toTypedArray())
    }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    val installedPackagesForRemove = installedPackages.mapNotNull {
      it.takeIf { it.name in pythonPackages }
    }
    val condaPackages = installedPackagesForRemove.filter { it is CondaPackage && !it.installedWithPip }
    val pipPackages = installedPackagesForRemove - condaPackages

    if (condaPackages.isNotEmpty()) {
      condaPackageEngine.uninstallPackageCommand(*condaPackages.map { it.name }.toTypedArray()).getOr {
        return it
      }
    }
    if (pipPackages.isNotEmpty()) {
      pipPackageEngine.uninstallPackageCommand(*pipPackages.map { it.name }.toTypedArray()).getOr {
        return it
      }
    }

    return PyResult.success(Unit)
  }

  private suspend fun performOperation(
    specifications: List<PythonRepositoryPackageSpecification>,
    operation: suspend (PythonPackageManagerEngine, List<PythonRepositoryPackageSpecification>) -> PyResult<Unit>,
  ): PyResult<Unit> {
    val engineWithSpecs = splitByEngine(specifications)
    engineWithSpecs.forEach { (manager, specs) ->
      if (specs.isEmpty())
        return@forEach
      operation(manager, specs).getOr {
        return it
      }
    }

    return PyResult.success(Unit)
  }

  private fun splitByEngine(specifications: List<PythonRepositoryPackageSpecification>) = specifications.groupBy { specification ->
    val repository = specification.repository
    if (repository is CondaPackageRepository)
      condaPackageEngine
    else
      pipPackageEngine
  }
}