// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonScripts")

package com.jetbrains.python.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.*
import com.intellij.execution.target.TargetEnvironment.TargetPath
import com.intellij.execution.target.TargetEnvironment.UploadRoot
import com.intellij.execution.target.local.LocalTargetPtyOptions
import com.intellij.execution.target.value.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.HelperPackage
import com.jetbrains.python.debugger.PyDebugRunner
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.run.target.PathMapping
import com.jetbrains.python.run.target.tryResolveAsPythonHelperDir
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.fixCondaPathEnvIfNeeded
import com.jetbrains.python.sdk.targetAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData.Companion.pathsAddedByUser
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

private val LOG = Logger.getInstance("#com.jetbrains.python.run.PythonScripts")


@JvmOverloads
fun PythonExecution.buildTargetedCommandLine(targetEnvironment: TargetEnvironment,
                                             sdk: Sdk?,
                                             interpreterParameters: List<String>,
                                             isUsePty: Boolean = false): TargetedCommandLine {
  val commandLineBuilder = TargetedCommandLineBuilder(targetEnvironment.request)
  workingDir?.apply(targetEnvironment)?.let { commandLineBuilder.setWorkingDirectory(it) }
  commandLineBuilder.charset = charset
  inputFile?.let { commandLineBuilder.setInputFile(TargetValue.fixed(it.absolutePath)) }

  when (this) {
    is PythonToolExecution -> {
      toolPath?.let {
        commandLineBuilder.exePath = TargetValue.fixed(it.pathString)
        commandLineBuilder.addParameters(listOf(*toolParams.toTypedArray()))
      }
    }
    else -> {
      sdk?.configureBuilderToRunPythonOnTarget(commandLineBuilder)
      commandLineBuilder.addParameters(interpreterParameters)
    }
  }

  when (this) {
    is PythonScriptExecution -> pythonScriptPath?.let { commandLineBuilder.addParameter(it.apply(targetEnvironment)) }
                                ?: throw IllegalArgumentException("Python script path must be set")
    is PythonModuleExecution -> moduleName?.let { commandLineBuilder.addParameters(listOf("-m", it)) }
                                ?: throw IllegalArgumentException("Python module name must be set")
    is PythonToolScriptExecution -> pythonScriptPath?.let { commandLineBuilder.addParameter(it.apply(targetEnvironment).pathString) }
                                    ?: throw IllegalArgumentException("Python script path must be set")
    is PythonToolModuleExecution -> moduleName?.let { moduleName ->
      moduleFlag?.let { moduleFlag ->
        commandLineBuilder.addParameters(listOf(moduleFlag, moduleName))
      } ?: throw IllegalArgumentException("Module flag must be set")
    } ?: throw IllegalArgumentException("Python module name must be set")

  }

  for (parameter in parameters) {
    val resolvedParameter = parameter.apply(targetEnvironment)
    if (resolvedParameter != PythonExecution.SKIP_ARGUMENT) {
      commandLineBuilder.addParameter(resolvedParameter)
    }
  }
  val userPathList = sdk?.targetAdditionalData?.pathsAddedByUser?.map { it.value }?.map { constant(it) }?.toMutableList() ?: ArrayList()
  initPythonPath(envs, true, userPathList, targetEnvironment.request, false)
  for ((name, value) in envs) {
    commandLineBuilder.addEnvironmentVariable(name, value.apply(targetEnvironment))
  }
  val environmentVariablesForVirtualenv = mutableMapOf<String, String>()
  // TODO [Targets API] It would be cool to activate environment variables for any type of target
  if (sdk != null) {
    PythonSdkType.patchEnvironmentVariablesForVirtualenv(environmentVariablesForVirtualenv, sdk)
  }
  // TODO [Targets API] [major] PATH env for virtualenv should extend existing PATH env
  environmentVariablesForVirtualenv.forEach { (name, value) -> commandLineBuilder.addEnvironmentVariable(name, value) }
  // TODO [Targets API] [major] `PythonSdkFlavor` should be taken into account to pass (at least) "IRONPYTHONPATH" or "JYTHONPATH"
  //  environment variables for corresponding interpreters
  if (isUsePty) {
    commandLineBuilder.ptyOptions = LocalTargetPtyOptions(LocalPtyOptions.defaults())
  }

  // This fix shouldn't be here, since flavor patches envs (see configureBuilderToRunPythonOnTarget), but envs
  // then overwritten by patchEnvironmentVariablesForVirtualenv and envs
  // Fix must be removed after path merging implementation
  if (sdk != null) {
    commandLineBuilder.fixCondaPathEnvIfNeeded(sdk)
  }
  return commandLineBuilder.build()
}


data class Upload(val localPath: String, val targetPath: TargetEnvironmentFunction<String>)

private fun resolveUploadPath(localPath: String, uploads: List<PathMapping>): TargetEnvironmentFunction<String> {
  val localFileSeparator = Platform.current().fileSeparator
  val matchUploads = uploads.mapNotNull { upload ->
    if (FileUtil.isAncestor(upload.localPath.toString(), localPath, false)) {
      FileUtil.getRelativePath(upload.localPath.toString(), localPath, localFileSeparator)?.let { upload to it }
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
  return upload.targetPathFun.getRelativeTargetPath(localRelativePath)
}

fun prepareHelperScriptExecution(helperPackage: HelperPackage,
                                 helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest): PythonScriptExecution =
  PythonScriptExecution().apply {
    val uploads = applyHelperPackageToPythonPath(helperPackage, helpersAwareTargetRequest)
    pythonScriptPath = resolveUploadPath(helperPackage.asParamString(), uploads)
  }

@ApiStatus.Internal
fun prepareHelperScriptViaToolExecution(
  helperPackage: HelperPackage,
  helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest,
  toolPath: Path,
  toolParams: List<String>,
): PythonToolScriptExecution {
  val envs: MutableMap<String, TargetEnvironmentFunction<String>> = mutableMapOf()
  val uploads = addHelperEntriesToPythonPath(envs, helperPackage.pythonPathEntries, helpersAwareTargetRequest)
  val execution = PythonToolScriptExecution(
    toolPath,
    toolParams,
    resolveUploadPath(helperPackage.asParamString(), uploads).andThen { Path.of(it) }
  )
  execution.envs += envs;
  return execution;
}

private const val PYTHONPATH_ENV = "PYTHONPATH"

/**
 * Requests the upload of PyCharm helpers root directory to the target.
 */
fun PythonExecution.applyHelperPackageToPythonPath(helperPackage: HelperPackage,
                                                   helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest): List<PathMapping> {
  return applyHelperPackageToPythonPath(helperPackage.pythonPathEntries, helpersAwareTargetRequest)
}

fun PythonExecution.applyHelperPackageToPythonPath(pythonPathEntries: List<String>,
                                                   helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest): List<PathMapping> {
  return addHelperEntriesToPythonPath(envs, pythonPathEntries, helpersAwareTargetRequest)
}

/**
 * @param envs the environment variables to be modified
 * @param pythonPathEntries the paths located either in PyCharm Community or in PyCharm helpers directories
 * @param helpersAwareTargetRequest the request
 * @param failOnError specifies whether the method should fail if one of [pythonPathEntries] is not located within PyCharm helpers dirs
 *
 * **Note.** This method assumes that PyCharm Community and PyCharm helpers are uploaded to the same root path on the target.
 * This assumption comes from [HelpersAwareTargetEnvironmentRequest.preparePyCharmHelpers] method that returns the single path value.
 */
fun addHelperEntriesToPythonPath(envs: MutableMap<String, TargetEnvironmentFunction<String>>,
                                 pythonPathEntries: List<String>,
                                 helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest,
                                 failOnError: Boolean = true): List<PathMapping> {
  val targetPlatform = helpersAwareTargetRequest.targetEnvironmentRequest.targetPlatform
  val pythonHelpersMappings = helpersAwareTargetRequest.preparePyCharmHelpers()
  val pythonHelpersRoots = pythonHelpersMappings.helpers.map(PathMapping::localPath)
  val targetPathSeparator = targetPlatform.platform.pathSeparator

  fun <T> T?.onResolutionFailure(message: String): T? {
    if (this != null)
      return this
    if (failOnError)
      error(message)
    LOG.error(message)
    return null
  }

  val uploads = pythonPathEntries.mapNotNull { pythonPathEntry ->
    pythonPathEntry
      .tryResolveAsPythonHelperDir(pythonHelpersMappings)
      .onResolutionFailure(message = "$pythonPathEntry cannot be resolved against Python helpers roots: ${pythonHelpersRoots}")
  }
  val pythonPathEntriesOnTarget = uploads.map { it.targetPathFun }
  val pythonPathValue = pythonPathEntriesOnTarget.joinToStringFunction(separator = targetPathSeparator.toString())
  appendToPythonPath(envs, pythonPathValue, targetPlatform)
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

    is PythonToolScriptExecution -> targetScript.pythonScriptPath?.let { pythonScriptPath -> addParameter(pythonScriptPath.andThen { it.pathString }) }
                                    ?: throw IllegalArgumentException("Python script path must be set")

    is PythonToolModuleExecution -> targetScript.moduleName?.let { moduleName ->
      targetScript.moduleFlag?.let { moduleFlag ->
        addParameters(moduleFlag, moduleName)
      } ?: throw java.lang.IllegalArgumentException("Module flag must be set")
    } ?: throw IllegalArgumentException("Python module name must be set")
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
  if (paths.isEmpty()) return
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

// TODO: Make this code python-agnostic, get red of path hardcode
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
 * You can also provide [modules] to add its content roots and [Sdk] for which user added custom paths
 */
fun TargetEnvironmentRequest.ensureProjectSdkAndModuleDirsAreOnTarget(project: Project, vararg modules: Module) {
  fun TargetEnvironmentRequest.addPathToVolume(basePath: Path) {
    if (uploadVolumes.none { basePath.startsWith(it.localRootPath) }) {
      uploadVolumes += UploadRoot(localRootPath = basePath, targetRootPath = TargetPath.Temporary())
    }
  }
  for (module in modules) {
    ModuleRootManager.getInstance(module).contentRoots.forEach {
      try {
        addPathToVolume(it.toNioPath())
      }
      catch (_: UnsupportedOperationException) {
        // VirtualFile.toNioPath throws UOE if VirtualFile has no associated path which is common case for JupyterRemoteVirtualFile
      }
    }
  }
  project.basePath?.let { addPathToVolume(Path.of(it)) }
}

/**
 * Mimics [PyDebugRunner.disableBuiltinBreakpoint] for [PythonExecution].
 */
fun PythonExecution.disableBuiltinBreakpoint(sdk: Sdk?) {
  if (sdk != null && PythonSdkFlavor.getFlavor(sdk)?.getLanguageLevel(sdk)?.isAtLeast(LanguageLevel.PYTHON37) == true) {
    addEnvironmentVariable("PYTHONBREAKPOINT", "0")
  }
}