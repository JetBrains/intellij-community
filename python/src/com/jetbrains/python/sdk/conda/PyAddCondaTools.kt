// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda

import com.intellij.execution.Platform
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.processTools.mapFlat
import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.platform.util.progress.RawProgressReporter
import com.jetbrains.python.conda.loadLocalPythonCondaPath
import com.jetbrains.python.conda.saveLocalPythonCondaPath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.asPythonResult
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.onFailure
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.conda.*
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

/**
 * Levels to be used for new conda envs
 */
internal val condaSupportedLanguages: List<LanguageLevel>
  get() = LanguageLevel.SUPPORTED_LEVELS
    .asReversed()
    .filter { it < LanguageLevel.PYTHON313 }

val condaLatestSupportedLanguage: LanguageLevel
  @ApiStatus.Internal get() =
    condaSupportedLanguages.maxWith(LanguageLevel.VERSION_COMPARATOR)

/**
 * See [com.jetbrains.env.conda.PyCondaSdkTest]
 */
suspend fun PyCondaCommand.createCondaSdkFromExistingEnv(
  condaIdentity: PyCondaEnvIdentity,
  existingSdks: List<Sdk>,
  project: Project?,
): Sdk {
  val condaEnv = PyCondaEnv(condaIdentity, fullCondaPathOnTarget)
  val flavorAndData = PyFlavorAndData(PyCondaFlavorData(condaEnv), CondaEnvSdkFlavor.getInstance())

  val additionalData = when (targetConfig) {
    null -> PythonSdkAdditionalData(flavorAndData)
    else -> PyTargetAwareAdditionalData(flavorAndData, targetConfig)
  }

  val sdk = ProjectJdkTable.getInstance().createSdk(SdkConfigurationUtil.createUniqueSdkName(condaIdentity.userReadableName, existingSdks),
                                                    PythonSdkType.getInstance())
  val sdkModificator = sdk.sdkModificator
  sdkModificator.sdkAdditionalData = additionalData
  // homePath is not required by conda, but used by lots of tools all over the code and required by CondaPathFix
  // Because homePath is not set yet, CondaPathFix does not work
  sdkModificator.homePath = getCondaPythonBinaryPath(project, condaEnv, targetConfig).onFailure {
    ShowingMessageErrorSync.emit(it)
  }.getOrThrow()
  edtWriteAction {
    sdkModificator.commitChanges()
  }
  saveLocalPythonCondaPath(Path.of(fullCondaPathOnTarget))
  return sdk
}

private const val PRINT_SYS_EXECUTABLE_SCRIPT = "import sys; print(sys.executable)"

/**
 * @return path to Conda interpreter binary on target
 */
private suspend fun getCondaPythonBinaryPath(
  project: Project?,
  condaEnv: PyCondaEnv,
  targetConfig: TargetEnvironmentConfiguration?,
): PyResult<FullPathOnTarget> =
  getCondaInterpreterOutput(project, condaEnv, EmptyProgressIndicator(), PRINT_SYS_EXECUTABLE_SCRIPT, targetConfig).mapSuccess { it.trim() }

/**
 * Runs python [command] and returns stdout or error
 */
private suspend fun getCondaInterpreterOutput(
  project: Project?,
  condaEnv: PyCondaEnv,
  indicator: ProgressIndicator,
  command: String,
  targetConfig: TargetEnvironmentConfiguration?,
): PyResult<String> {
  val targetEnvRequest = targetConfig?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest()

  val cmdBuilder = TargetedCommandLineBuilder(targetEnvRequest)
  addCondaPythonToTargetCommandLine(cmdBuilder, condaEnv, sdk = null)
  cmdBuilder.addParameter("-c")
  cmdBuilder.addParameter(command)
  val cmd = cmdBuilder.build()

  val environment = targetEnvRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))
  return withContext(Dispatchers.IO) {
    environment.createProcessWithResult(cmd).mapFlat { it.getResultStdoutStr() }.asPythonResult()
  }
}

/**
 * See [com.jetbrains.env.conda.PyCondaSdkTest]
 */
suspend fun PyCondaCommand.createCondaSdkAlongWithNewEnv(
  newCondaEnvInfo: NewCondaEnvRequest,
  uiContext: CoroutineContext,
  existingSdks: List<Sdk>,
  project: Project,
  reporter: RawProgressReporter? = null,
): PyResult<Sdk> {
  PyCondaEnv.createEnv(this, newCondaEnvInfo).getOr { return it }
  val sdk = createCondaSdkFromExistingEnv(newCondaEnvInfo.toIdentity(), existingSdks, project)
  saveLocalPythonCondaPath(Path.of(this@createCondaSdkAlongWithNewEnv.fullCondaPathOnTarget))

  return PyResult.success(sdk)
}

private fun NewCondaEnvRequest.toIdentity(): PyCondaEnvIdentity =
  when (this) {
    is NewCondaEnvRequest.EmptyNamedEnv, is NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile -> PyCondaEnvIdentity.NamedEnv(envName)
    is NewCondaEnvRequest.EmptyUnnamedEnv -> PyCondaEnvIdentity.UnnamedEnv(envPath = envName, isBase = false)
  }

/**
 * Detects conda binary in well-known locations on the local machine.
 */
suspend fun suggestCondaPath(filter: (FullPathOnTarget) -> Boolean = { true }): FullPathOnTarget? {
  return suggestCondaPath(TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest()), filter)
}

/**
 * Detects conda binary in well-known locations on target
 */
internal suspend fun suggestCondaPath(targetCommandExecutor: TargetCommandExecutor, filter: (FullPathOnTarget) -> Boolean = { true }): FullPathOnTarget? {
  val targetPlatform = withContext(Dispatchers.IO) {
    targetCommandExecutor.targetPlatform.await()
  }
  var possiblePaths: Array<FullPathOnTarget> = when (targetPlatform.platform) {
    Platform.UNIX -> arrayOf(
      "~/anaconda3/bin/conda",
      "~/miniconda3/bin/conda",
      "/usr/local/bin/conda",
      "~/opt/miniconda3/condabin/conda",
      "~/opt/anaconda3/condabin/conda",
      "/opt/miniconda3/condabin/conda",
      "/opt/conda/bin/conda",
      "/opt/anaconda3/condabin/conda",
      "/opt/homebrew/anaconda3/bin/conda",
    )
    Platform.WINDOWS -> arrayOf(
      "%ALLUSERSPROFILE%\\Anaconda3\\condabin\\conda.bat",
      "%ALLUSERSPROFILE%\\Miniconda3\\condabin\\conda.bat",
      "%USERPROFILE%\\Anaconda3\\condabin\\conda.bat",
      "%USERPROFILE%\\Miniconda3\\condabin\\conda.bat",
    )
  }
  // If conda is local then store path
  if (targetCommandExecutor.isLocalMachineExecutor) {
    loadLocalPythonCondaPath()?.let {
      possiblePaths = arrayOf(it.pathString) + possiblePaths
    }
  }
  return possiblePaths.firstNotNullOfOrNull { targetCommandExecutor.getExpandedPathIfExecutable(it)?.takeIf { filter(it) } }
}

private val TargetCommandExecutor.isLocalMachineExecutor: Boolean
  get() = this is TargetEnvironmentRequestCommandExecutor && request is LocalTargetEnvironmentRequest

private fun TargetCommandExecutor.executeShellCommand(command: String): CompletableFuture<ProcessOutput> =
  targetPlatform
    .thenApply { targetPlatform -> command.asCommandInShell(targetPlatform) }
    .thenCompose { commandInShell -> execute(commandInShell) }

private fun String.asCommandInShell(targetPlatform: TargetPlatform): List<String> =
  if (targetPlatform.platform == Platform.WINDOWS) listOf("cmd.exe", "/c", this)
  else listOf("sh", "-c", this)

/**
 * If [file] is executable returns it in expanded (env vars resolved) manner.
 */
private suspend fun TargetCommandExecutor.getExpandedPathIfExecutable(file: FullPathOnTarget): FullPathOnTarget? = withContext(
  Dispatchers.IO) {
  if (isLocalMachineExecutor) {
    val expandedPath = expandPathLocally(file)
    return@withContext if (Path.of(expandedPath).isExecutable()) expandedPath else null
  }
  else {
    val expandedPath = executeShellCommand("echo $file").thenApply(ProcessOutput::getStdout).thenApply(String::trim).await()
    // TODO: Should we test with browsable target as well?

    if (targetPlatform.await().platform == Platform.WINDOWS) {
      logger<TargetCommandExecutor>().warn("Remote windows target not supported")
      return@withContext null
    }
    return@withContext if (executeShellCommand("test -x $expandedPath").await().exitCode == 0) expandedPath
    else null
  }
}

@ApiStatus.Internal
interface TargetCommandExecutor {
  val targetPlatform: CompletableFuture<TargetPlatform>
  fun execute(command: List<String>): CompletableFuture<ProcessOutput>

  /**
   * Command will be executed on local machine
   */
  val local: Boolean
}

@ApiStatus.Internal
class TargetEnvironmentRequestCommandExecutor(internal val request: TargetEnvironmentRequest) : TargetCommandExecutor {
  override val targetPlatform: CompletableFuture<TargetPlatform> = CompletableFuture.completedFuture(request.targetPlatform)
  override val local: Boolean = request is LocalTargetEnvironmentRequest
  override fun execute(command: List<String>): CompletableFuture<ProcessOutput> {
    val commandLineBuilder = TargetedCommandLineBuilder(request)
    commandLineBuilder.setExePath(command.first())
    commandLineBuilder.addParameters(command.subList(fromIndex = 1, toIndex = command.size))
    val process = request.prepareEnvironment(TargetProgressIndicator.EMPTY).createProcess(commandLineBuilder.build())
    return CompletableFuture.supplyAsync({ process.captureProcessOutput(command) }, ProcessIOExecutorService.INSTANCE)
  }
}

private fun Process.captureProcessOutput(commandLine: List<String>): ProcessOutput {
  val commandLineString = commandLine.joinToString(separator = " ")
  return CapturingProcessHandler(this, Charsets.UTF_8, commandLineString).runProcess()
}

internal class IntrospectableCommandExecutor(private val introspectable: LanguageRuntimeType.Introspectable) : TargetCommandExecutor {
  override val local: Boolean = false // we never introspect local machine for now
  override val targetPlatform: CompletableFuture<TargetPlatform>
    get() = introspectable.targetPlatform

  override fun execute(command: List<String>): CompletableFuture<ProcessOutput> = introspectable.promiseExecuteScript(command)
}

internal fun Sdk.isConda(): Boolean {
  if (!PythonSdkUtil.isPythonSdk(this)) {
    return false
  }

  return getOrCreateAdditionalData().flavorAndData.data is PyCondaFlavorData
}