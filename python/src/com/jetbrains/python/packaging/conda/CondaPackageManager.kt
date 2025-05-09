// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerRunner
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.targetEnvConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

private fun PythonPackageInstallRequest.buildInstallationArguments(): Result<List<String>> = when (this) {
  is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecification -> {
    when (this.specification.repository) {
      is CondaPackageRepository -> Result.success(listOf(this.specification.nameWithVersionSpec))
      else -> Result.failure(UnsupportedOperationException("CondaManager installer supports only conda repositories, got ${this.specification.repository} "))
    }
  }
  is PythonPackageInstallRequest.AllRequirements -> Result.success(emptyList())
  is PythonPackageInstallRequest.ByLocation -> Result.failure(UnsupportedOperationException("CondaManager does not support installing from location uri"))
}

@ApiStatus.Experimental
class CondaPackageManager(project: Project, sdk: Sdk) : PythonPackageManager(project, sdk) {
  @Volatile
  override var installedPackages: List<PythonPackage> = emptyList()
  override val repositoryManager: PythonRepositoryManager = CondaRepositoryManger(project, sdk)

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

  override suspend fun updatePackageCommand(specification: PythonRepositoryPackageSpecification): Result<Unit> =
    try {
      runConda("update", listOf(specification.name, "-y"), message("conda.packaging.update.progress", specification.name))
      Result.success(Unit)
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }

  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit> =
    try {
      runConda("uninstall", listOf(pkg.name, "-y"), message("conda.packaging.uninstall.progress", pkg.name))
      Result.success(Unit)
    }
    catch (ex: ExecutionException) {
      Result.failure(ex)
    }

  override suspend fun reloadPackagesCommand(): Result<List<PythonPackage>> =
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
      .map { line -> line.split("\\s+".toRegex()) }
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

      val result = PythonPackageManagerRunner.runProcess(this@CondaPackageManager, process, commandLineString, text, withBackgroundProgress)

      result.checkSuccess(thisLogger())
      if (result.isTimeout) throw PyExecutionException(message("conda.packaging.exception.timeout"), operation, arguments, result)
      if (result.exitCode != 0) {
        throw PyExecutionException(message("conda.packaging.exception.non.zero"), operation, arguments, result)
      }
      else result.stdout
    }
  }
}