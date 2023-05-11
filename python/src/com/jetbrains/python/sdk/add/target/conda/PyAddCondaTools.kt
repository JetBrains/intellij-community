// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.target.conda

import com.intellij.execution.Platform
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.conda.*
import com.jetbrains.python.sdk.getPythonBinaryPath
import com.jetbrains.python.target.PyTargetAwareAdditionalData
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
val condaSupportedLanguages: List<LanguageLevel>
  get() = LanguageLevel.SUPPORTED_LEVELS
    .asReversed()
    .filter { it < LanguageLevel.PYTHON311 }

/**
 * See [com.jetbrains.env.conda.PyCondaSdkTest]
 */
suspend fun PyCondaCommand.createCondaSdkFromExistingEnv(condaIdentity: PyCondaEnvIdentity,
                                                         existingSdks: List<Sdk>,
                                                         project: Project): Sdk {
  val flavorAndData = PyFlavorAndData(PyCondaFlavorData(PyCondaEnv(condaIdentity, fullCondaPathOnTarget)), CondaEnvSdkFlavor.getInstance())

  val additionalData = when (targetConfig) {
    null -> PythonSdkAdditionalData(flavorAndData)
    else -> PyTargetAwareAdditionalData(flavorAndData, targetConfig)
  }

  val sdk = ProjectJdkImpl(SdkConfigurationUtil.createUniqueSdkName(condaIdentity.userReadableName, existingSdks),
                           PythonSdkType.getInstance())
  sdk.sdkAdditionalData = additionalData
  // homePath is not required by conda, but used by lots of tools all over the code and required by CondaPathFix
  // Because homePath is not set yet, CondaPathFix does not work
  sdk.homePath = sdk.getPythonBinaryPath(project).getOrThrow()
  saveLocalPythonCondaPath(Path.of(fullCondaPathOnTarget))
  return sdk
}

/**
 * See [com.jetbrains.env.conda.PyCondaSdkTest]
 */
suspend fun PyCondaCommand.createCondaSdkAlongWithNewEnv(newCondaEnvInfo: NewCondaEnvRequest,
                                                         uiContext: CoroutineContext,
                                                         existingSdks: List<Sdk>,
                                                         project: Project,
                                                         sink: ProgressSink? = null): Result<Sdk> {
  val process = PyCondaEnv.createEnv(this, newCondaEnvInfo).getOrElse { return Result.failure(it) }
  val error = ProcessHandlerReader(process).runProcessAndGetError(uiContext, sink)

  return error?.let { Result.failure(Exception(it)) }
         ?: Result.success(
           createCondaSdkFromExistingEnv(PyCondaEnvIdentity.NamedEnv(newCondaEnvInfo.envName), existingSdks, project)).apply {
           onSuccess {
             saveLocalPythonCondaPath(Path.of(this@createCondaSdkAlongWithNewEnv.fullCondaPathOnTarget))
           }
         }
}

/**
 * Detects conda binary in well-known locations on the local machine.
 */
suspend fun suggestCondaPath(): FullPathOnTarget? {
  return suggestCondaPath(TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest()))
}

/**
 * Detects conda binary in well-known locations on target
 */
internal suspend fun suggestCondaPath(targetCommandExecutor: TargetCommandExecutor): FullPathOnTarget? {
  val targetPlatform = withContext(Dispatchers.IO) {
    targetCommandExecutor.targetPlatform.await()
  }
  var possiblePaths: Array<FullPathOnTarget> = when (targetPlatform.platform) {
    Platform.UNIX -> arrayOf("~/anaconda3/bin/conda",
                             "~/miniconda3/bin/conda",
                             "/usr/local/bin/conda",
                             "~/opt/miniconda3/condabin/conda",
                             "~/opt/anaconda3/condabin/conda",
                             "/opt/miniconda3/condabin/conda",
                             "/opt/conda/bin/conda",
                             "/opt/anaconda3/condabin/conda")
    Platform.WINDOWS -> arrayOf("%ALLUSERSPROFILE%\\Anaconda3\\condabin\\conda.bat",
                                "%ALLUSERSPROFILE%\\Miniconda3\\condabin\\conda.bat",
                                "%USERPROFILE%\\Anaconda3\\condabin\\conda.bat",
                                "%USERPROFILE%\\Miniconda3\\condabin\\conda.bat"
    )
  }
  // If conda is local then store path
  if (targetCommandExecutor.isLocalMachineExecutor) {
    loadLocalPythonCondaPath()?.let {
      possiblePaths = arrayOf(it.pathString) + possiblePaths
    }
  }
  return possiblePaths.firstNotNullOfOrNull { targetCommandExecutor.getExpandedPathIfExecutable(it) }
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
  val expandedPath = executeShellCommand("echo $file").thenApply(ProcessOutput::getStdout).thenApply(String::trim).await()
  if (isLocalMachineExecutor) {
    return@withContext if (Path.of(expandedPath).isExecutable()) expandedPath else null
  }
  else {

    // TODO: Should we test with browsable target as well?

    if (targetPlatform.await().platform == Platform.WINDOWS) {
      logger<PyAddCondaPanelModel>().warn("Remote windows target not supported")
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
}

@ApiStatus.Internal
class TargetEnvironmentRequestCommandExecutor(internal val request: TargetEnvironmentRequest) : TargetCommandExecutor {
  override val targetPlatform: CompletableFuture<TargetPlatform> = CompletableFuture.completedFuture(request.targetPlatform)

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
  override val targetPlatform: CompletableFuture<TargetPlatform>
    get() = introspectable.targetPlatform

  override fun execute(command: List<String>): CompletableFuture<ProcessOutput> = introspectable.promiseExecuteScript(command)
}