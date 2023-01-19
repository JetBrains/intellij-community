// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.pip.PipBasedPackageManager
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.targetEnvConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
class CondaPackageManager(project: Project, sdk: Sdk) : PipBasedPackageManager(project, sdk) {

  override var installedPackages: List<CondaPackage> = emptyList()
    private set

  override val repositoryManager: CondaRepositoryManger = CondaRepositoryManger(project, sdk)

  override suspend fun installPackage(specification: PythonPackageSpecification): Result<List<PythonPackage>> {
    return if (specification is CondaPackageSpecification) withContext(Dispatchers.IO) {
      runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.new.project.install.failed.title", specification.name), specification.name) {
        runConda("install", specification.buildInstallationString() + "-y", PyBundle.message("conda.packaging.install.progress", specification.name))
        reloadPackages()
      }
    }
    else return super.installPackage(specification)
  }

  override suspend fun uninstallPackage(pkg: PythonPackage): Result<List<PythonPackage>> {
    return if (pkg is CondaPackage && !pkg.installedWithPip) withContext(Dispatchers.IO) {
      runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.packaging.operation.failed.title")) {
        runConda("uninstall", listOf(pkg.name, "-y"), PyBundle.message("conda.packaging.uninstall.progress", pkg.name))
        reloadPackages()
      }
    }
    else super.uninstallPackage(pkg)
  }

  override suspend fun reloadPackages(): Result<List<PythonPackage>> {
    return withContext(Dispatchers.IO) {
      val result = runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.packaging.operation.failed.title")) {
        val output = runConda("list", emptyList(), PyBundle.message("conda.packaging.list.progress"))
        Result.success(parseCondaPackageList(output))
      }
      if (result.isFailure) return@withContext result

      withContext(Dispatchers.Main) {
        installedPackages = result.getOrThrow()
      }

      ApplicationManager.getApplication()
        .messageBus
        .syncPublisher(PACKAGE_MANAGEMENT_TOPIC)
        .packagesChanged(sdk)

      result
    }
  }

  private fun parseCondaPackageList(text: String): List<CondaPackage> {
    return text.lineSequence()
      .filterNot { it.startsWith("#") }
      .map { line -> line.split("\\s+".toRegex()) }
      .filterNot { it.size < 2 }
      .map { CondaPackage(it[0], it[1], installedWithPip = (it.size >= 4 && it[3] == "pypi")) }
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

      val result = withBackgroundProgressIndicator(project, text, cancellable = true) {
        handler.runProcess(10 * 60 * 1000)
      }

      result.checkSuccess(thisLogger())
      if (result.isTimeout) throw PyExecutionException("Time out", operation, arguments, result)
      if (result.exitCode != 0) {
        throw PyExecutionException("Non-zero exit code", operation, arguments, result)
      }

      else result.stdout
    }
  }

}