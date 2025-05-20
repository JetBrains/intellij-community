// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerEngine
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipPackageManagerEngine
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class CondaWithPipFallbackPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  private val condaPackageEngine = CondaPackageManagerEngine(project, sdk)
  private val condaRepositoryManger = CondaRepositoryManger(project, sdk)
  private val pipRepositoryManger = PipRepositoryManager(project)
  private val pipPackageEngine = PipPackageManagerEngine(project, sdk)

  override var repositoryManager: PythonRepositoryManager = CompositePythonRepositoryManager(project,
                                                                                             listOf(condaRepositoryManger, pipRepositoryManger))

  override suspend fun loadPackagesCommand(): Result<List<PythonPackage>> = condaPackageEngine.loadPackagesCommand()

  override suspend fun loadOutdatedPackagesCommand(): Result<List<PythonOutdatedPackage>> = coroutineScope {
    val condaOutdated = async {
      condaPackageEngine.loadOutdatedPackagesCommand()
    }
    val pipOutdated = async {
      pipPackageEngine.loadOutdatedPackagesCommand()
    }

    val condaPackages = condaOutdated.await().getOrElse {
      return@coroutineScope Result.failure(it)
    }
    val pipPackages = pipOutdated.await().getOrElse {
      return@coroutineScope Result.failure(it)
    }

    val onlyPipOutdated = pipPackages.filter { outdatedPackage ->
      val pythonPackage = installedPackages.firstOrNull { it.name == outdatedPackage.name } ?: return@filter false
      pythonPackage !is CondaPackage || pythonPackage.installedWithPip
    }
    Result.success(condaPackages + onlyPipOutdated)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit> = when (installRequest) {
    PythonPackageInstallRequest.AllRequirements -> condaPackageEngine.installPackageCommand(installRequest, options)
    is PythonPackageInstallRequest.ByLocation -> pipPackageEngine.installPackageCommand(installRequest, options)
    is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> installSeveralPackages(installRequest.specifications, options)
  }

  private suspend fun installSeveralPackages(specifications: List<PythonRepositoryPackageSpecification>, options: List<String>) =
    performOperation(specifications) { manager, specs ->
      val managerRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specs)
      manager.installPackageCommand(managerRequest, options)
    }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): Result<Unit> =
    performOperation(specifications.toList()) { manager, specs ->
      manager.updatePackageCommand(*specs.toTypedArray())
    }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): Result<Unit> {
    val installedPackagesForRemove = installedPackages.mapNotNull {
      it.takeIf { it.name in pythonPackages }
    }
    val condaPackages = installedPackagesForRemove.filter { it is CondaPackage && !it.installedWithPip }
    val pipPackages = installedPackagesForRemove - condaPackages

    if (condaPackages.isNotEmpty()) {
      condaPackageEngine.uninstallPackageCommand(*condaPackages.map { it.name }.toTypedArray()).onFailure {
        return Result.failure(it)
      }
    }
    if (pipPackages.isNotEmpty()) {
      pipPackageEngine.uninstallPackageCommand(*pipPackages.map { it.name }.toTypedArray()).onFailure {
        return Result.failure(it)
      }
    }

    return Result.success(Unit)
  }

  private suspend fun performOperation(
    specifications: List<PythonRepositoryPackageSpecification>,
    operation: suspend (PythonPackageManagerEngine, List<PythonRepositoryPackageSpecification>) -> Result<Unit>,
  ): Result<Unit> {
    val engineWithSpecs = splitByEngine(specifications)
    engineWithSpecs.forEach { (manager, specs) ->
      if (specs.isEmpty())
        return@forEach
      operation(manager, specs).onFailure {
        return Result.failure(it)
      }
    }

    return Result.success(Unit)
  }

  private fun splitByEngine(specifications: List<PythonRepositoryPackageSpecification>) = specifications.groupBy { specification ->
    val repository = specification.repository
    if (repository is CondaPackageRepository)
      condaPackageEngine
    else
      pipPackageEngine
  }
}