// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonPackageManagerExt")

package com.jetbrains.python.packaging.management

import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.net.HttpConfigurable
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.ensureProjectSdkAndModuleDirsAreOnTarget
import com.jetbrains.python.run.prepareHelperScriptExecution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.math.min

@ApiStatus.Internal
@RequiresBackgroundThread
fun PythonPackageManager.launchReload() {
  runBlockingCancellable {
    runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.packaging.operation.failed.title")) {
      reloadPackages()
    }
  }
}

@ApiStatus.Internal
suspend fun PythonPackageManager.runPackagingTool(
  operation: String, arguments: List<String>, @Nls text: String,
  withBackgroundProgress: Boolean = true,
): String = withContext(Dispatchers.IO) {
  // todo[akniazev]: check for package management tools
  val helpersAwareTargetRequest = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project)
  val targetEnvironmentRequest = helpersAwareTargetRequest.targetEnvironmentRequest
  val pythonExecution = blockingContext { prepareHelperScriptExecution(PythonHelper.PACKAGING_TOOL, helpersAwareTargetRequest) }

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

  val process = blockingContext { targetEnvironment.createProcess(targetedCommandLine, indicator) }

  val commandLine = targetedCommandLine.collectCommandsSynchronously()
  val commandLineString = commandLine.joinToString(" ")

  thisLogger().debug("Running python packaging tool. Operation: $operation")

  val result = PythonPackageManagerRunner.runProcess(this@runPackagingTool, process, commandLineString, text, withBackgroundProgress)
  if (result.isCancelled) throw RunCanceledByUserException()
  result.checkSuccess(thisLogger())
  val exitCode = result.exitCode
  val helperPath = commandLine.firstOrNull() ?: ""
  val args: List<String> = commandLine.subList(min(1, commandLine.size), commandLine.size)
  if (exitCode != 0) {
    val message = if (result.stdout.isBlank() && result.stderr.isBlank()) PySdkBundle.message(
      "python.conda.permission.denied")
    else PySdkBundle.message("python.sdk.packaging.non.zero.exit.code", exitCode)
    throw PyExecutionException(message, helperPath, args, result)
  }

  if (result.isTimeout) {
    throw PyExecutionException.createForTimeout(PySdkBundle.message("python.sdk.packaging.timed.out"), helperPath, args)
  }

  return@withContext result.stdout
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

@ApiStatus.Internal
fun PythonRepositoryManager.packagesByRepository(): Sequence<Pair<PyPackageRepository, Set<String>>> {
  return repositories.asSequence().map { it to it.getPackages() }
}

@ApiStatus.Internal
fun PythonPackageManager.isInstalled(name: String): Boolean {
  return installedPackages.any { it.name.equals(name, ignoreCase = true) }
}

/**
 * Finds the first repository that contains the package with the given name.
 * Fallbacks to the main (first) repository if nothing is found, just to keep existing behavior.
 * It looks like this fallback should be reviewed, possibly removed and tested.
 */
@ApiStatus.Internal
fun PythonRepositoryManager.findPackageRepository(packageName: String): PyPackageRepository? {
  val normalizePackageName = normalizePackageName(packageName)
  val repository = packagesByRepository().firstNotNullOfOrNull { (repository, packageNames) ->
    val contains = packageNames.contains(normalizePackageName) ||
                   packageNames.any { normalizePackageName(it) == normalizePackageName }
    if (contains) repository else null
  }
  return repository ?: repositories.firstOrNull()
}