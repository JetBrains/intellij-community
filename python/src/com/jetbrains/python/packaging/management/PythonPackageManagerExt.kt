// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonPackageManagerExt")
package com.jetbrains.python.packaging.management

import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.net.HttpConfigurable
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.prepareHelperScriptExecution
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import kotlin.math.min

fun PythonPackageManager.launchReload() {
  ApplicationManager.getApplication().coroutineScope.launch {
    reloadPackages()
  }
}

suspend fun PythonPackageManager.runPackagingTool(operation: String, arguments: List<String>, @Nls text: String): String {
  // todo[akniazev]: check for package management tools
  val helpersAwareTargetRequest = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project)
  val targetEnvironmentRequest = helpersAwareTargetRequest.targetEnvironmentRequest
  val pythonExecution = prepareHelperScriptExecution(PythonHelper.PACKAGING_TOOL, helpersAwareTargetRequest)

  // todo[akniazev]: check applyWorkingDir: PyTargetEnvironmentPackageManager.java:133

  pythonExecution.addParameter(operation)

  proxyString?.let {
    pythonExecution.addParameter("--proxy")
    pythonExecution.addParameter(it)
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
  val handler = CapturingProcessHandler(process, targetedCommandLine.charset, commandLineString)

  val result = withBackgroundProgressIndicator(project, text, cancellable = true) {
    handler.runProcess(10 * 60 * 1000)
  }

  if (result.isCancelled) throw RunCanceledByUserException()
  result.checkSuccess(thisLogger())
  val exitCode = result.exitCode
  val helperPath = commandLine.firstOrNull() ?: ""
  val args: List<String> = commandLine.subList(min(1, commandLine.size), commandLine.size)
  if (exitCode != 0) {
    val message = if (StringUtil.isEmptyOrSpaces(result.stdout) && StringUtil.isEmptyOrSpaces(result.stderr)) PySdkBundle.message(
      "python.conda.permission.denied")
    else PySdkBundle.message("python.sdk.packaging.non.zero.exit.code", exitCode)
    throw PyExecutionException(message, helperPath, args, result)
  }

  if (result.isTimeout) {
    throw PyExecutionException(PySdkBundle.message("python.sdk.packaging.timed.out"), helperPath, args, result)
  }

  return result.stdout
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

fun PythonRepositoryManager.packagesByRepository(): Sequence<Pair<PyPackageRepository, List<String>>> {
  return repositories.asSequence().map { it to packagesFromRepository(it) }
}