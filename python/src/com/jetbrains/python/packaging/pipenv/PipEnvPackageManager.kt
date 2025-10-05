// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pipenv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.dependencies.PythonDependenciesManager
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.pipenv.runPipEnv
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class PipEnvPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  private val modulePath: Path?
    get() = sdk.associatedModulePath?.let { Path.of(it) }

  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)

  override suspend fun syncCommand(): PyResult<Unit> {
    return runPipEnv(modulePath, "install", "--dev").mapSuccess { }
  }

  suspend fun lock(): PyResult<Unit> {
    return runPipEnv(modulePath, "lock").mapSuccess { }
  }


  override fun getDependencyManager(): PythonDependenciesManager {
    return PipEnvDependenciesManager.getInstance(project, sdk)
  }


  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    return when (installRequest) {
      is PythonPackageInstallRequest.ByLocation -> TODO("Not yet implemented")
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {

        val args = listOf("install") + installRequest.specifications.map { it.nameWithVersionSpec } + options
        runPipEnv(modulePath, *args.toTypedArray()).mapSuccess { }
      }
    }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val args = listOf("install") + specifications.map { it.nameWithVersionSpec }
    return runPipEnv(modulePath, *args.toTypedArray()).mapSuccess { }
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    val args = listOf("uninstall") + pythonPackages.toList()
    return runPipEnv(modulePath, *args.toTypedArray()).mapSuccess { }

  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val output = runPipEnv(modulePath, "graph", "--json")
    output.getOr {
      return it
    }
    val parsed = output.mapSuccess { json ->
      val parsed = PipEnvParser.parsePipEnvGraph(json)
      val pyPackages = PipEnvParser.parsePipEnvGraphEntries(parsed)
      pyPackages.map { PythonPackage(it.name, it.version, false) }
    }

    return parsed
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    //There is no normal way to get outdated packages
    //https://github.com/pypa/pipenv/issues/1490
    return PyResult.success(emptyList())
  }

}