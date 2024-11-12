// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withRawProgressReporter
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.pip.PipPythonPackageManager
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.targetEnvConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
class CondaPackageManager(project: Project, sdk: Sdk) : PipPythonPackageManager(project, sdk) {
  override val repositoryManager = CondaRepositoryManger(project, sdk)

  override suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<String> =
    if (specification is CondaPackageSpecification) {
      try {
        Result.success(runConda("install", specification.buildInstallationString() + "-y" + options, message("conda.packaging.install.progress", specification.name)))
      }
      catch (ex: ExecutionException) {
        Result.failure(ex)
      }
    }
    else {
      super.installPackageCommand(specification, options)
    }

  override suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<String> =
    if (specification is CondaPackageSpecification) {
      try {
        Result.success(runConda("update", listOf(specification.name, "-y"), message("conda.packaging.update.progress", specification.name)))
      }
      catch (ex: ExecutionException) {
        Result.failure(ex)
      }
    }
    else {
      super.updatePackageCommand(specification)
    }


  override suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<String> =
    if (pkg is CondaPackage && !pkg.installedWithPip) {
      try {
        Result.success(runConda("uninstall", listOf(pkg.name, "-y"), message("conda.packaging.uninstall.progress", pkg.name)))
      }
      catch (ex: ExecutionException) {
        Result.failure(ex)
      }
    }
    else {
      super.uninstallPackageCommand(pkg)
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


  private suspend fun runConda(operation: String, arguments: List<String>, @Nls text: String): String {
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
      val handler = CapturingProcessHandler(process, targetedCommandLine.charset, commandLineString)

      val result = withBackgroundProgress(project, text, true) {
        withRawProgressReporter<ProcessOutput?> {
          handler.runProcess(10 * 60 * 1000)
        } as ProcessOutput
      }

      result.checkSuccess(thisLogger())
      if (result.isTimeout) throw PyExecutionException(message("conda.packaging.exception.timeout"), operation, arguments, result)
      if (result.exitCode != 0) {
        throw PyExecutionException(message("conda.packaging.exception.non.zero"), operation, arguments, result)
      }

      else result.stdout
    }
  }
}