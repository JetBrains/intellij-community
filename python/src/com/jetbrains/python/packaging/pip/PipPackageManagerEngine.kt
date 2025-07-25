// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.IdeUtilIoBundle
import com.intellij.util.net.HttpConfigurable
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerEngine
import com.jetbrains.python.packaging.management.PythonPackageManagerRunner
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.ensureProjectSdkAndModuleDirsAreOnTarget
import com.jetbrains.python.run.prepareHelperScriptExecution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.math.min


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
      arguments = listOf("-r", file.path)
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

  @ApiStatus.Internal
  suspend fun runPackagingTool(operation: String, arguments: List<String>): PyResult<String> = withContext(Dispatchers.IO) {
    // todo[akniazev]: check for package management tools
    val helpersAwareTargetRequest = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project)
    val targetEnvironmentRequest = helpersAwareTargetRequest.targetEnvironmentRequest
    val pythonExecution = prepareHelperScriptExecution(PythonHelper.PACKAGING_TOOL, helpersAwareTargetRequest)

    if (targetEnvironmentRequest is LocalTargetEnvironmentRequest) {
      if (Registry.`is`("python.packaging.tool.use.project.location.as.working.dir")) {
        project.guessProjectDir()?.toNioPath()?.let {
          pythonExecution.workingDir = targetPath(it)
        }
      }
    }
    else {
      if (Registry.`is`("python.packaging.tool.upload.project")) {
        project.guessProjectDir()?.toNioPath()?.let {
          targetEnvironmentRequest.ensureProjectSdkAndModuleDirsAreOnTarget(project)
          pythonExecution.workingDir = targetPath(it)
        }
      }
    }

    pythonExecution.addParameter(operation)
    if (operation == "install") {
      proxyString?.let {
        pythonExecution.addParameter("--proxy")
        pythonExecution.addParameter(it)
      }
    }

    arguments.forEach(pythonExecution::addParameter)

    // // todo[akniazev]: add extra args to package specification

    val targetProgressIndicator = TargetProgressIndicator.EMPTY
    val targetEnvironment = targetEnvironmentRequest.prepareEnvironment(targetProgressIndicator)

    targetEnvironment.uploadVolumes.entries.forEach { (_, value) ->
      value.upload(".", targetProgressIndicator)
    }

    val targetedCommandLine = pythonExecution.buildTargetedCommandLine(targetEnvironment, sdk, emptyList())

    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    // from targets package manager
    // TODO [targets] Apply environment variables: setPythonUnbuffered(...), setPythonDontWriteBytecode(...), resetHomePathChanges(...)
    // TODO [targets] Apply flavor from PythonSdkFlavor.getFlavor(mySdk)
    // TODO [targets] check askForSudo

    val process = targetEnvironment.createProcess(targetedCommandLine, indicator)

    val commandLine = targetedCommandLine.collectCommandsSynchronously()
    val commandLineString = commandLine.joinToString(" ")

    thisLogger().debug("Running python packaging tool. Operation: $operation")

    val result = PythonPackageManagerRunner.runProcess(
      process,
      commandLineString
    )
    if (result.isCancelled) {
      return@withContext PyResult.localizedError(IdeUtilIoBundle.message("run.canceled.by.user.message"))
    }

    result.checkSuccess(thisLogger())
    val exitCode = result.exitCode
    val helperPath = commandLine.firstOrNull() ?: ""
    val args: List<String> = commandLine.subList(min(1, commandLine.size), commandLine.size)
    if (exitCode != 0) {
      val message = if (result.stdout.isBlank() && result.stderr.isBlank()) PySdkBundle.message(
        "python.conda.permission.denied")
      else PySdkBundle.message("python.sdk.packaging.non.zero.exit.code", exitCode)
      PyExecutionException(message, commandLine[0], args, result).let {
        return@withContext PyResult.failure(it.pyError)
      }
    }

    if (result.isTimeout) {
      PyExecutionException.createForTimeout(PySdkBundle.message("python.sdk.packaging.timed.out"), helperPath, args).let {
        return@withContext PyResult.failure(it.pyError)
      }
    }

    return@withContext PyResult.success(result.stdout)
  }

  private val proxyString: String?
    get() {
      val settings = HttpConfigurable.getInstance()
      if (settings != null && settings.USE_HTTP_PROXY) {
        val credentials = if (settings.PROXY_AUTHENTICATION) "${settings.proxyLogin}:${settings.plainProxyPassword}@" else ""
        return "http://$credentials${settings.PROXY_HOST}:${settings.PROXY_PORT}"
      }
      return null
    }

  private fun partitionPackagesBySource(installRequest: PythonPackageInstallRequest): List<List<String>> {
    when (installRequest) {
      is PythonPackageInstallRequest.ByLocation -> {
        return listOf(listOf(installRequest.location.toString()))
      }
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {
        return partitionPackagesBySource(installRequest.specifications)
      }
    }
  }

  private fun partitionPackagesBySource(specifications: List<PythonRepositoryPackageSpecification>): List<List<String>> {
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

        listOf(
          "--index-url",
          url
        ) + specs.map { it.nameWithVersionSpec }
      }

    val pypi = mutableListOf<List<String>>()
    if (pypiSpecs.isNotEmpty()) {
      pypi.add(pypiSpecs.map { it.nameWithVersionsSpec })
    }

    return pypi + byRepository
  }

  suspend fun performInstall(argumentsGroups: List<List<String>>, options: List<String>): PyResult<Unit> {
    for (argumentsGroup in argumentsGroups) {
      val result = runPackagingTool(
        operation = "install",
        arguments = argumentsGroup + options
      )

      result.onFailure {
        return PyResult.failure(it)
      }
    }

    return PyResult.success(Unit)
  }
}