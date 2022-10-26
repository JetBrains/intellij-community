// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.target.conda

import com.intellij.execution.Platform
import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.execution.target.FullPathOnTarget
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.conda.*
import com.jetbrains.python.sdk.getPythonBinaryPath
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.isExecutable

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
  sdk.homePath = sdk.getPythonBinaryPath(project).getOrThrow()
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
         ?: Result.success(createCondaSdkFromExistingEnv(PyCondaEnvIdentity.NamedEnv(newCondaEnvInfo.envName), existingSdks, project))
}

/**
 * Detects conda binary in well-known locations on target
 */
suspend fun suggestCondaPath(configuration: TargetEnvironmentConfiguration?): FullPathOnTarget? {
  val request = configuration?.createEnvironmentRequest(null) ?: LocalTargetEnvironmentRequest()
  val possiblePaths: Array<FullPathOnTarget> = when (request.targetPlatform.platform) {
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
  return possiblePaths.firstNotNullOfOrNull { request.getExpandedPathIfExecutable(it) }
}


private suspend fun TargetEnvironmentRequest.executeShellCommand(command: String): Process {
  val commandLine = TargetedCommandLineBuilder(this).apply {
    if (targetPlatform.platform == Platform.WINDOWS) {
      setExePath("cmd.exe")
      addParameter("/c")
    }
    else {
      setExePath("sh")
      addParameter("-c")
    }
    addParameter(command)
  }.build()
  return prepareEnvironment(TargetProgressIndicator.EMPTY).createProcessWithResult(commandLine).getOrThrow()
}

/**
 * If [file] is executable returns it in expanded (env vars resolved) manner.
 */
suspend fun TargetEnvironmentRequest.getExpandedPathIfExecutable(file: FullPathOnTarget): FullPathOnTarget? = withContext(Dispatchers.IO) {
  val expandedPath = executeShellCommand("echo $file").getResultStdoutStr().getOrElse {
    logger<PyAddCondaPanelModel>().warn(it)
    return@withContext null
  }
  if (this@getExpandedPathIfExecutable is LocalTargetEnvironmentRequest) {
    return@withContext if (Path.of(expandedPath).isExecutable()) expandedPath else null
  }
  else {

    // TODO: Should we test with browsable target as well?

    if (targetPlatform.platform == Platform.WINDOWS) {
      logger<PyAddCondaPanelModel>().warn("Remote windows target not supported")
      return@withContext null
    }
    return@withContext if (executeShellCommand("test -x $expandedPath").getBareExecutionResult().exitCode == 0) expandedPath
    else null
  }
}