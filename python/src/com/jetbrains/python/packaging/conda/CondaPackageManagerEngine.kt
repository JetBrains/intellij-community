// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.eel.provider.asEelPath
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result.Companion.success
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.asExecutionFailed
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManagerEngine
import com.jetbrains.python.packaging.management.PythonPackageManagerRunner
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.targetEnvConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import kotlin.io.path.Path

internal class CondaPackageManagerEngine(
  private val project: Project,
  private val sdk: Sdk,
) : PythonPackageManagerEngine {
  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    val jsonPyResult = runConda("update", listOf("--dry-run", "--all", "--json"),
                                message("conda.packaging.list.outdated.progress"),
                                withBackgroundProgress = false).getOr { return it }

    val parsed = withContext(Dispatchers.Default) {
      CondaParseUtils.parseOutdatedOutputs(jsonPyResult)
    }
    return PyResult.success(parsed)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    val installationArgs = installRequest.buildInstallationArguments().getOr { return it }
    val result = runConda("install", installationArgs + "-y" + options, message("conda.packaging.install.progress", installRequest.title),
                          withBackgroundProgress = false)
    return result.mapSuccess { }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> {
    val packages = specifications.map { it.name }
    val result = runConda("install", packages + listOf("-y"),
                          message("conda.packaging.update.progress", packages.joinToString(", ")))
    return result.mapSuccess { }
  }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit> {
    if (pythonPackages.isEmpty())
      return PyResult.success(Unit)

    val result = runConda("uninstall", pythonPackages.toList() + listOf("-y"),
                          message("conda.packaging.uninstall.progress", pythonPackages.joinToString(", ")),
                          withBackgroundProgress = false)
    return result.mapSuccess { }
  }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
    val result = runConda("list", emptyList(), message("conda.packaging.list.progress"))
    return result.mapSuccess {
      parseCondaPackageList(it)
    }
  }

  private fun parseCondaPackageList(text: String): List<CondaPackage> {
    return text.lineSequence()
      .filterNot { it.startsWith("#") }
      .map { line -> line.split(listLineParser) }
      .filterNot { it.size < 2 }
      //TODO: fix
      .map { CondaPackage(it[0], it[1], editableMode = false, installedWithPip = (it.size >= 4 && it[3] == "pypi")) }
      .sortedWith(compareBy(CondaPackage::name))
      .toList()
  }


  private suspend fun runConda(operation: String, arguments: List<String>, @Nls text: String, withBackgroundProgress: Boolean = true): PyResult<String> {
    return withContext(Dispatchers.IO) {
      val targetConfig = sdk.targetEnvConfiguration
      val targetReq = targetConfig?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest()
      val commandLineBuilder = TargetedCommandLineBuilder(targetReq)
      val targetEnv = targetReq.prepareEnvironment(TargetProgressIndicator.EMPTY)
      val env = (sdk.getOrCreateAdditionalData().flavorAndData.data as PyCondaFlavorData).env

      commandLineBuilder.setExePath(env.fullCondaPathOnTarget)
      commandLineBuilder.addParameter(operation)
      env.addCondaEnvironmentToTargetBuilder(commandLineBuilder)
      arguments.forEach(commandLineBuilder::addParameter)

      val targetedCommandLine = commandLineBuilder.build()
      val process = targetEnv.createProcess(targetedCommandLine)
      val commandLine = targetedCommandLine.collectCommandsSynchronously()
      val commandLineString = StringUtil.join(commandLine, " ")

      val result = PythonPackageManagerRunner.runProcess(project, process, commandLineString, text, withBackgroundProgress)

      result.checkSuccess(thisLogger())
      if (result.isTimeout) throw PyExecutionException(message("conda.packaging.exception.timeout"), operation, arguments, result)
      if (result.exitCode != 0) {
        val error = ExecError(Path(env.fullCondaPathOnTarget).asEelPath(), (listOf(operation) + arguments).toTypedArray(), result.asExecutionFailed())
        failure(error)
      }
      else {
        success(result.stdout)
      }
    }
  }

  private fun PythonPackageInstallRequest.buildInstallationArguments(): PyResult<List<String>> = when (this) {
    is PythonPackageInstallRequest.AllRequirements -> PyResult.success(emptyList())
    is PythonPackageInstallRequest.ByLocation -> PyResult.localizedError("CondaManager does not support installing from location uri")
    is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {
      val condaSpecs = specifications.filter { it.repository is CondaPackageRepository }
      val specs = condaSpecs.map { it.nameWithVersionSpec }

      //https://docs.conda.io/projects/conda/en/latest/user-guide/concepts/pkg-specs.html#package-match-specifications
      //When using the command line, put double quotes around any package version specification that
      // contains the space character or any of the following characters: <, >, *, or |.
      //Ido not know why we need put single prefix quota but it does not work in EEL with suffix quota
      val quoted = specs.map { "\"$it" }

      PyResult.success(quoted)
    }
  }

  companion object {
    private val listLineParser = "\\s+".toRegex()
  }
}