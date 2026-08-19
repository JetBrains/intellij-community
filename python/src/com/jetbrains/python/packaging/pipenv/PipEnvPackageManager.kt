// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pipenv

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.orLogException
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.common.toPythonPackages
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.packageRequirements.CachedDependencyTreeProvider
import com.jetbrains.python.packaging.packageRequirements.DependencyTreeProvider
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.sdk.pipenv.PIP_FILE
import com.jetbrains.python.sdk.pipenv.runPipEnvWithSdk
import java.nio.file.Path
import com.jetbrains.python.sdk.pipenv.PipEnvParser as SdkPipEnvParser

internal class PipEnvPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  override val repositoryManager: PythonRepositoryManager = PipRepositoryManager.getInstance(project)

  override val treeProvider: DependencyTreeProvider = CachedDependencyTreeProvider(
    fetchOutput = { runPipEnvWithSdk(sdk, "graph", "--json").orLogException(thisLogger()) },
    parse = { json -> PipEnvParser.parsePipEnvGraph(json).map { it.toTreeNode() } },
  )

  override suspend fun syncLockedCommand(): PyResult<Unit> {
    return runPipEnvWithSdk(sdk, "install", "--dev").mapSuccess { }
  }

  suspend fun lock(): PyResult<Unit> {
    return runPipEnvWithSdk(sdk, "lock").mapSuccess { }
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>, module: Module?, dependencyGroup: PyDependencyGroup?): PyResult<Unit> {
    return when (installRequest) {
      is PythonPackageInstallRequest.ByLocation -> TODO("Not yet implemented")
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {

        val args = buildList {
          addAll(listOf("install"))
          installRequest.specifications.mapTo(this) { it.nameWithVersionSpecs }
          addAll(options)
        }
        runPipEnvWithSdk(sdk, *args.toTypedArray()).mapSuccess { }
      }
    }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val args = buildList {
      addAll(listOf("install"))
      specifications.mapTo(this) { it.nameWithVersionSpecs }
    }
    return runPipEnvWithSdk(sdk, *args.toTypedArray()).mapSuccess { }
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember?, dependencyGroup: PyDependencyGroup?): PyResult<Unit> {
    val args = listOf("uninstall") + pythonPackages.toList()
    return runPipEnvWithSdk(sdk, *args.toTypedArray()).mapSuccess { }

  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val output = runPipEnvWithSdk(sdk, "graph", "--json")
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
    val output = runPipEnvWithSdk(sdk, "update", "--dry-run").getOr { return it }
    val outdated = PipEnvParser.parseOutdatedPackagesOutput(output)

    return PyResult.success(outdated)
  }

  override suspend fun listDeclaredPackages(): PyResult<List<PythonPackage>>? {
    val pipFileLock = getRootDependenciesFile() ?: return null
    val requirements = SdkPipEnvParser.getPipFileLockRequirements(pipFileLock.virtualFile) ?: return null
    return PyResult.success(requirements.toPythonPackages())
  }

  override val dependenciesFilesRelativePaths: List<Path>
    get() =
      listOf(Path.of(PIP_FILE))
}

private fun PipEnvParser.GraphEntry.toTreeNode(): PackageTreeNode =
  PackageTreeNode(
    PyPackageName.from(pkg.packageName),
    dependencies.mapTo(mutableListOf()) { PackageTreeNode(PyPackageName.from(it.packageName), version = it.installedVersion) },
    version = pkg.installedVersion,
  )
