// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PyBundle.message
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

internal class CondaPackageManagerEngine(
  private val project: Project,
  private val sdk: Sdk,
) : PythonPackageManagerEngine {
  override suspend fun loadOutdatedPackagesCommand(): Result<List<PythonOutdatedPackage>> {
    val jsonResult = try {
      runConda("update", listOf("--dry-run", "--all", "--json"),
               message("conda.packaging.list.outdated.progress"),
               withBackgroundProgress = false)
    }
    catch (ex: ExecutionException) {
      return Result.failure(ex)
    }

    val parsed = withContext(Dispatchers.Default) {
      CondaParseUtils.parseOutdatedOutputs(jsonResult)
    }
    return Result.success(parsed)
  }

  override suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit> {
    val installationArgs = installRequest.buildInstallationArguments().getOrElse { return Result.failure(it) }
    return try {
      runConda("install", installationArgs + "-y" + options, message("conda.packaging.install.progress", installRequest.title),
               withBackgroundProgress = false)
      Result.success(Unit)
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): Result<Unit> =
    try {
      val packages = specifications.map { it.name }
      runConda("install", packages + listOf("-y"),
               message("conda.packaging.update.progress", packages.joinToString(", ")))
      Result.success(Unit)
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }

  override suspend fun uninstallPackageCommand(vararg pythonPackages: String): Result<Unit> {
    if (pythonPackages.isEmpty())
      return Result.success(Unit)

    return try {
      runConda("uninstall", pythonPackages.toList() + listOf("-y"),
               message("conda.packaging.uninstall.progress", pythonPackages.joinToString(", ")),
               withBackgroundProgress = false)
      Result.success(Unit)
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }
  }

  override suspend fun loadPackagesCommand(): Result<List<PythonPackage>> =
    try {
      val output = runConda("list", emptyList(), message("conda.packaging.list.progress"))
      Result.success(parseCondaPackageList(output))
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
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


  private suspend fun runConda(operation: String, arguments: List<String>, @Nls text: String, withBackgroundProgress: Boolean = true): String {
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
        throw PyExecutionException(message("conda.packaging.exception.non.zero"), env.fullCondaPathOnTarget, listOf(operation) + arguments, result)
      }
      else result.stdout
    }
  }

  private fun PythonPackageInstallRequest.buildInstallationArguments(): Result<List<String>> = when (this) {
    is PythonPackageInstallRequest.AllRequirements -> Result.success(emptyList())
    is PythonPackageInstallRequest.ByLocation -> Result.failure(UnsupportedOperationException("CondaManager does not support installing from location uri"))
    is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {
      val condaSpecs = specifications.filter { it.repository is CondaPackageRepository }
      val specs = condaSpecs.map { it.nameWithVersionSpec }

      //https://docs.conda.io/projects/conda/en/latest/user-guide/concepts/pkg-specs.html#package-match-specifications
      //When using the command line, put double quotes around any package version specification that
      // contains the space character or any of the following characters: <, >, *, or |.
      //Ido not know why we need put single prefix quota but it does not work in EEL with suffix quota
      val quoted = specs.map { "\"$it" }

      Result.success(quoted)
    }
  }

  companion object {
    private val listLineParser = "\\s+".toRegex()
  }
}