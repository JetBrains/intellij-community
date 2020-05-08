// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PythonScripts")

package com.jetbrains.python.run

import com.intellij.execution.Platform
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.target.*
import com.intellij.execution.target.value.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.HelperPackage
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.sdk.PythonSdkType

private val LOG = logger("#com.jetbrains.python.run.PythonScripts")

fun PythonExecution.buildTargetedCommandLine(targetEnvironment: TargetEnvironment,
                                             sdk: Sdk?,
                                             interpreterParameters: List<String>): TargetedCommandLine {
  val commandLineBuilder = TargetedCommandLineBuilder(targetEnvironment.request)
  workingDir?.apply(targetEnvironment)?.let { commandLineBuilder.setWorkingDirectory(it) }
  charset?.let { commandLineBuilder.setCharset(it) }
  val interpreterPath = getInterpreterPath(sdk)
  if (!interpreterPath.isNullOrEmpty()) {
    commandLineBuilder.setExePath(FileUtil.toSystemDependentName(interpreterPath))
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
private fun getInterpreterPath(sdk: Sdk?): String? {
  if (sdk == null) return null
  return sdk.sdkAdditionalData?.let { (it as? PyRemoteSdkAdditionalDataBase)?.interpreterPath } ?: sdk.homePath
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

fun prepareHelperScriptExecution(helperPackage: HelperPackage, targetEnvironmentRequest: TargetEnvironmentRequest): PythonScriptExecution =
  PythonScriptExecution().apply {
    val uploads = applyHelperPackageToPythonPath(helperPackage, targetEnvironmentRequest)
    pythonScriptPath = resolveUploadPath(helperPackage.asParamString(), uploads)
  }

private const val PYTHONPATH_ENV = "PYTHONPATH"

/**
 * Requests the upload of PyCharm helpers root directory to the target.
 */
fun PythonExecution.applyHelperPackageToPythonPath(helperPackage: HelperPackage,
                                                   targetEnvironmentRequest: TargetEnvironmentRequest): Iterable<Upload> {
  // TODO [Targets API] Helpers scripts should be synchronized by the version of the IDE
  val localHelpersRootPath = PythonHelpersLocator.getHelpersRoot().absolutePath
  val uploadRoot = TargetEnvironment.UploadRoot(localRootPath = PythonHelpersLocator.getHelpersRoot().toPath(),
                                                targetRootPath = TargetEnvironment.TargetPath.Temporary())
  targetEnvironmentRequest.uploadVolumes += uploadRoot
  val targetUploadPath = uploadRoot.getTargetUploadPath()
  val targetPlatform = targetEnvironmentRequest.targetPlatform
  val targetPathSeparator = targetPlatform.platform.pathSeparator
  val uploads = helperPackage.pythonPathEntries.map {
    // TODO [Targets API] Simplify the paths resolution
    val relativePath = FileUtil.getRelativePath(localHelpersRootPath, it, Platform.current().fileSeparator)
                       ?: throw IllegalStateException("Helpers PYTHONPATH entry '$it' cannot be resolved" +
                                                      " against the root path of PyCharm helpers '$localHelpersRootPath'")
    Upload(it, targetUploadPath.getRelativeTargetPath(relativePath))
  }
  val pythonPathEntries = uploads.map { it.targetPath }
  val pythonPathValue = pythonPathEntries.joinToStringFunction(separator = targetPathSeparator.toString())
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