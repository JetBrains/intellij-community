// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.python.HelperName
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerEngine
import com.jetbrains.python.packaging.utils.PyProxyUtils
import com.jetbrains.python.sdk.executeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class PipPackageManagerEngine(
  private val project: Project,
  private val sdk: Sdk,
) : PythonPackageManagerEngine {
  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    val manager = PythonPackageManager.forSdk(project, sdk)

    PipManagementInstaller(sdk, manager).installManagementIfNeeded()

    val argumentsGroups = partitionPackagesBySource(installRequest)
    return performInstall(argumentsGroups, options)
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    val output = runPackagingTool("list_outdated", listOf()).getOr { return it }
    val packages = PipParseUtils.parseOutdatedOutputs(output)
    return PyResult.success(packages)
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val argumentsGroups = partitionPackagesBySource(specifications.toList())
    return performInstall(argumentsGroups, listOf("--upgrade"))
  }

  suspend fun syncProject(): PyResult<Unit> {
    return runPackagingTool(
      operation = "install",
      arguments = listOf(".")
    ).mapSuccess { }
  }

  suspend fun syncRequirementsTxt(file: VirtualFile): PyResult<Unit> {
    return runPackagingTool(
      operation = "install",
      arguments = Args("-r").addLocalFile(file.toNioPath())
    ).mapSuccess { }
  }


  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    val result = runPackagingTool(
      operation = "uninstall",
      arguments = pythonPackages.toList()
    )
    return result.mapSuccess { }
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val output = runPackagingTool(
      operation = "list",
      arguments = emptyList()
    ).getOr { return it }

    val packages = PipParseUtils.parseListResult(output)
    return PyResult.success(packages)
  }

  private suspend fun runPackagingTool(operation: String, arguments: Args): PyResult<String> = withContext(Dispatchers.IO) {
    val parameters = Args(operation)
    if (operation == "install") {
      PyProxyUtils.proxyString?.let {
        parameters.addArgs("--proxy", it)
      }
    }
    parameters.add(arguments)

    thisLogger().debug("Running python packaging tool. Operation: $operation")
    ExecService().executeHelper(
      sdk,
      PACKAGING_TOOL_NAME,
      parameters,
    )
  }

  private suspend fun runPackagingTool(operation: String, arguments: List<String>): PyResult<String> =
    runPackagingTool(operation, Args(*arguments.toTypedArray()))


  private fun partitionPackagesBySource(installRequest: PythonPackageInstallRequest): List<Args> {
    when (installRequest) {
      is PythonPackageInstallRequest.ByLocation -> {
        return listOf(Args(installRequest.location.toString()))
      }
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {
        return partitionPackagesBySource(installRequest.specifications)
      }
    }
  }

  private fun partitionPackagesBySource(specifications: List<PythonRepositoryPackageSpecification>): List<Args> {
    val (pypiSpecs, nonPypi) = specifications.partition {
      val url = it.repository.urlForInstallation?.toString()
      url == null || url == PyPIPackageUtil.PYPI_LIST_URL
    }

    val byRepository = nonPypi
      .groupBy { it.repository.repositoryUrl }
      .mapNotNull { (url, specs) ->
        if (url == null || specs.isEmpty()) {
          return@mapNotNull null
        }

        val argsStr = listOf(
          "--index-url",
          url
        ) + specs.map { it.nameWithVersionSpec }

        Args().addArgs(argsStr)
      }

    val pypi = mutableListOf<Args>()
    if (pypiSpecs.isNotEmpty()) {
      pypi.add(Args().addArgs(pypiSpecs.map { it.nameWithVersionsSpec }))
    }

    return pypi + byRepository
  }

  suspend fun performInstall(argumentsGroups: List<Args>, options: List<String>): PyResult<Unit> {
    for (argumentsGroup in argumentsGroups) {
      val result = runPackagingTool(
        operation = "install",
        arguments = argumentsGroup.addArgs(options)
      )

      result.onFailure {
        return PyResult.failure(it)
      }
    }

    return PyResult.success(Unit)
  }

  companion object {
    const val PACKAGING_TOOL_NAME: HelperName = "packaging_tool.py"
  }
}
