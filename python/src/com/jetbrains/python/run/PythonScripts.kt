// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PythonScripts")

package com.jetbrains.python.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.*
import com.intellij.execution.target.TargetEnvironment.TargetPath
import com.intellij.execution.target.TargetEnvironment.UploadRoot
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.execution.target.value.joinToStringFunction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.remote.RemoteSdkPropertiesPaths
import com.intellij.util.io.isAncestor
import com.jetbrains.python.HelperPackage
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.PythonSdkType
import java.nio.file.Path

private val LOG = Logger.getInstance("#com.jetbrains.python.run.PythonScripts")

fun PythonExecution.buildTargetedCommandLine(targetEnvironment: TargetEnvironment,
                                             sdk: Sdk?,
                                             interpreterParameters: List<String>): TargetedCommandLine {
  val commandLineBuilder = TargetedCommandLineBuilder(targetEnvironment.request)
  workingDir?.apply(targetEnvironment)?.let { commandLineBuilder.setWorkingDirectory(it) }
  charset?.let { commandLineBuilder.setCharset(it) }
  val interpreterPath = getInterpreterPath(sdk)
  if (!interpreterPath.isNullOrEmpty()) {
    commandLineBuilder.setExePath(targetEnvironment.targetPlatform.platform.toSystemDependentName(interpreterPath))
  }
  commandLineBuilder.addParameters(interpreterParameters)
  when (this) {
    is PythonScriptExecution -> pythonScriptPath?.let { commandLineBuilder.addParameter(it.apply(targetEnvironment)) }
                                ?: throw IllegalArgumentException("Python script path must be set")
    is PythonModuleExecution -> moduleName?.let { commandLineBuilder.addParameters(listOf("-m", moduleName)) }
                                ?: throw IllegalArgumentException("Python module name must be set")
  }
  for (parameter in parameters) {
    commandLineBuilder.addParameter(parameter.apply(targetEnvironment))
  }
  for ((name, value) in envs) {
    commandLineBuilder.addEnvironmentVariable(name, value.apply(targetEnvironment))
  }
  val environmentVariablesForVirtualenv = mutableMapOf<String, String>()
  // TODO [Targets API] It would be cool to activate environment variables for any type of target
  sdk?.let { PythonSdkType.patchEnvironmentVariablesForVirtualenv(environmentVariablesForVirtualenv, it) }
  // TODO [Targets API] [major] PATH env for virtualenv should extend existing PATH env
  environmentVariablesForVirtualenv.forEach { (name, value) -> commandLineBuilder.addEnvironmentVariable(name, value) }
  // TODO [Targets API] [major] `PythonSdkFlavor` should be taken into account to pass (at least) "IRONPYTHONPATH" or "JYTHONPATH"
  //  environment variables for corresponding interpreters
  return commandLineBuilder.build()
}

/**
 * Returns the path to Python interpreter executable. The path is the path on
 * the target environment.
 */
fun getInterpreterPath(sdk: Sdk?): String? {
  if (sdk == null) return null
  // `RemoteSdkPropertiesPaths` suits both `PyRemoteSdkAdditionalDataBase` and `PyTargetAwareAdditionalData`
  return sdk.sdkAdditionalData?.let { (it as? RemoteSdkPropertiesPaths)?.interpreterPath } ?: sdk.homePath
}

data class Upload(val localPath: String, val targetPath: TargetEnvironmentFunction<String>)

private fun resolveUploadPath(localPath: String, uploads: Iterable<Upload>): TargetEnvironmentFunction<String> {
  val localFileSeparator = Platform.current().fileSeparator
  val matchUploads = uploads.mapNotNull { upload ->
    if (FileUtil.isAncestor(upload.localPath, localPath, false)) {
      FileUtil.getRelativePath(upload.localPath, localPath, localFileSeparator)?.let { upload to it }
    }
    else {
      null
    }
  }
  if (matchUploads.size > 1) {
    LOG.warn("Several uploads matches the local path '$localPath': $matchUploads")
  }
  val (upload, localRelativePath) = matchUploads.firstOrNull()
                                    ?: throw IllegalStateException("Failed to find uploads for the local path '$localPath'")
  return upload.targetPath.getRelativeTargetPath(localRelativePath)
}

fun prepareHelperScriptExecution(helperPackage: HelperPackage,
                                 helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest): PythonScriptExecution =
  PythonScriptExecution().apply {
    val uploads = applyHelperPackageToPythonPath(helperPackage, helpersAwareTargetRequest)
    pythonScriptPath = resolveUploadPath(helperPackage.asParamString(), uploads)
  }

private const val PYTHONPATH_ENV = "PYTHONPATH"

/**
 * Requests the upload of PyCharm helpers root directory to the target.
 */
fun PythonExecution.applyHelperPackageToPythonPath(helperPackage: HelperPackage,
                                                   helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest): Iterable<Upload> {
  return applyHelperPackageToPythonPath(helperPackage.pythonPathEntries, helpersAwareTargetRequest)
}

fun PythonExecution.applyHelperPackageToPythonPath(pythonPathEntries: List<String>,
                                                   helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest): Iterable<Upload> {
  val localHelpersRootPath = PythonHelpersLocator.getHelpersRoot().absolutePath
  val targetPlatform = helpersAwareTargetRequest.targetEnvironmentRequest.targetPlatform
  val targetUploadPath = helpersAwareTargetRequest.preparePyCharmHelpers()
  val targetPathSeparator = targetPlatform.platform.pathSeparator
  val uploads = pythonPathEntries.map {
    // TODO [Targets API] Simplify the paths resolution
    val relativePath = FileUtil.getRelativePath(localHelpersRootPath, it, Platform.current().fileSeparator)
                       ?: throw IllegalStateException("Helpers PYTHONPATH entry '$it' cannot be resolved" +
                                                      " against the root path of PyCharm helpers '$localHelpersRootPath'")
    Upload(it, targetUploadPath.getRelativeTargetPath(relativePath))
  }
  val pythonPathEntriesOnTarget = uploads.map { it.targetPath }
  val pythonPathValue = pythonPathEntriesOnTarget.joinToStringFunction(separator = targetPathSeparator.toString())
  appendToPythonPath(pythonPathValue, targetPlatform)
  return uploads
}

/**
 * Suits for coverage and profiler scripts.
 */
fun PythonExecution.addPythonScriptAsParameter(targetScript: PythonExecution) {
  when (targetScript) {
    is PythonScriptExecution -> targetScript.pythonScriptPath?.let { pythonScriptPath -> addParameter(pythonScriptPath) }
                                ?: throw IllegalArgumentException("Python script path must be set")
    is PythonModuleExecution -> targetScript.moduleName?.let { moduleName -> addParameters("-m", moduleName) }
                                ?: throw IllegalArgumentException("Python module name must be set")
  }
}

fun PythonExecution.addParametersString(parametersString: String) {
  ParametersList.parse(parametersString).forEach { parameter -> addParameter(parameter) }
}

private fun PythonExecution.appendToPythonPath(value: TargetEnvironmentFunction<String>, targetPlatform: TargetPlatform) {
  appendToPythonPath(envs, value, targetPlatform)
}

fun appendToPythonPath(envs: MutableMap<String, TargetEnvironmentFunction<String>>,
                       value: TargetEnvironmentFunction<String>,
                       targetPlatform: TargetPlatform) {
  envs.merge(PYTHONPATH_ENV, value) { whole, suffix ->
    listOf(whole, suffix).joinToPathValue(targetPlatform)
  }
}

fun appendToPythonPath(envs: MutableMap<String, TargetEnvironmentFunction<String>>,
                       paths: Collection<TargetEnvironmentFunction<String>>,
                       targetPlatform: TargetPlatform) {
  val value = paths.joinToPathValue(targetPlatform)
  envs.merge(PYTHONPATH_ENV, value) { whole, suffix ->
    listOf(whole, suffix).joinToPathValue(targetPlatform)
  }
}

/**
 * Joins the provided paths collection to single [TargetValue] using target
 * platform's path separator.
 *
 * The result is applicable for `PYTHONPATH` and `PATH` environment variables.
 */
fun Collection<TargetEnvironmentFunction<String>>.joinToPathValue(targetPlatform: TargetPlatform): TargetEnvironmentFunction<String> =
  this.joinToStringFunction(separator = targetPlatform.platform.pathSeparator.toString())

fun PythonExecution.extendEnvs(additionalEnvs: Map<String, TargetEnvironmentFunction<String>>, targetPlatform: TargetPlatform) {
  for ((key, value) in additionalEnvs) {
    if (key == PYTHONPATH_ENV) {
      appendToPythonPath(value, targetPlatform)
    }
    else {
      addEnvironmentVariable(key, value)
    }
  }
}

/**
 * Execute this command in a given environment, throwing an `ExecutionException` in case of a timeout or a non-zero exit code.
 */
@Throws(ExecutionException::class)
fun TargetedCommandLine.execute(env: TargetEnvironment, indicator: ProgressIndicator): ProcessOutput {
  val process = env.createProcess(this, indicator)
  val capturingHandler = CapturingProcessHandler(process, charset, getCommandPresentation(env))
  val output = capturingHandler.runProcess()
  if (output.isTimeout || output.exitCode != 0) {
    val fullCommand = collectCommandsSynchronously()
    throw PyExecutionException("", fullCommand[0], fullCommand.drop(1), output)
  }
  return output
}

/**
 * Checks whether the base directory of [project] is registered in [this] request. Adds it if it is not.
 */
fun TargetEnvironmentRequest.ensureProjectDirIsOnTarget(project: Project) {
  val basePath = project.basePath?.let { Path.of(it) } ?: return
  if (uploadVolumes.none { it.localRootPath.isAncestor(basePath) }) {
    uploadVolumes += UploadRoot(localRootPath = basePath, targetRootPath = TargetPath.Temporary())
  }
}