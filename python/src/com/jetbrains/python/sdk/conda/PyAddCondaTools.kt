// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.jetbrains.python.conda.loadLocalPythonCondaPath
import com.jetbrains.python.conda.saveLocalPythonCondaPath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.SdkCreationRequest
import com.jetbrains.python.sdk.ToolCommandExecutor
import com.jetbrains.python.sdk.ToolSearchPath
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import com.jetbrains.python.sdk.pyRichSdk
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

private val CONDA_TOOL: ToolCommandExecutor = ToolCommandExecutor(
  "conda",
  additionalSearchPaths = listOf(
    ToolSearchPath.RelativePathFromHome(listOf("anaconda3", "bin"), Platform.UNIX),
    ToolSearchPath.RelativePathFromHome(listOf("miniconda3", "bin"), Platform.UNIX),
    ToolSearchPath.AbsolutePath("/usr/local/bin", Platform.UNIX),
    ToolSearchPath.RelativePathFromHome(listOf("opt", "miniconda3", "bin"), Platform.UNIX),
    ToolSearchPath.RelativePathFromHome(listOf("opt", "anaconda3", "bin"), Platform.UNIX),
    ToolSearchPath.AbsolutePath("/opt/miniconda3/condabin", Platform.UNIX),
    ToolSearchPath.AbsolutePath("/opt/conda/bin", Platform.UNIX),
    ToolSearchPath.AbsolutePath("/opt/anaconda3/condabin", Platform.UNIX),
    ToolSearchPath.AbsolutePath("/opt/homebrew/anaconda3/bin", Platform.UNIX),
    ToolSearchPath.RelativePath("ALLUSERSPROFILE", listOf("Anaconda3", "condabin"), Platform.WINDOWS),
    ToolSearchPath.RelativePath("ALLUSERSPROFILE", listOf("Miniconda3", "condabin"), Platform.WINDOWS),
    ToolSearchPath.RelativePath("USERPROFILE", listOf("Anaconda3", "condabin"), Platform.WINDOWS),
    ToolSearchPath.RelativePath("USERPROFILE", listOf("Miniconda3", "condabin"), Platform.WINDOWS),
  ),
  getToolPathFromSettings = { loadLocalPythonCondaPath()?.pathString }
)

/**
 * Levels to be used for new conda envs
 */
internal val condaSupportedLanguages: List<LanguageLevel>
  get() = LanguageLevel.SUPPORTED_LEVELS
    .asReversed()
    .filter { it < LanguageLevel.PYTHON314 }


@Deprecated("Use `createCondaSdkFromExistingEnvironment` instead")
suspend fun PyCondaCommand.createCondaSdkFromExistingEnv(
  condaIdentity: PyCondaEnvIdentity,
  existingSdks: List<Sdk>,
  @Suppress("unused", "UNUSED_PARAMETER") project: Project? = null,
): Sdk = createCondaSdkFromExistingEnvironment(condaIdentity, existingSdks).getOrThrow()


/**
 * See `com.jetbrains.env.python.conda.PyCondaSdkTest`
 */
internal suspend fun PyCondaCommand.createCondaSdkFromExistingEnvironment(
  condaIdentity: PyCondaEnvIdentity,
  existingSdks: List<Sdk>,
): PyResult<Sdk> {
  val condaEnv = PyCondaEnv(condaIdentity, fullCondaPathOnTarget)
  val flavorAndData = PyFlavorAndData(PyCondaFlavorData(condaEnv), CondaEnvSdkFlavor)
  val interpreterPath = getCondaPythonBinaryPath(condaEnv, targetConfig).getOr { return it }

  val targetConfig = targetConfig
  val creationRequest = if (targetConfig == null) {
    SdkCreationRequest.EelSdk(Path(interpreterPath), PythonSdkAdditionalData(flavorAndData))
  }
  else {
    val addData = PyTargetAwareAdditionalData(flavorAndData, targetConfig).also {
      it.interpreterPath = interpreterPath
    }
    SdkCreationRequest.TargetSdk(interpreterPath, addData)
  }

  val sdkType = PythonSdkType.getInstance()
  val name = SdkConfigurationUtil.createUniqueSdkName(sdkType.suggestSdkName(null, interpreterPath), existingSdks)
  val sdk = creationRequest.createSdk( name).getOr { return it }

  sdk.pyRichSdk()
  if (targetConfig == null) {
    saveLocalPythonCondaPath(Path.of(fullCondaPathOnTarget))
  }
  sdkType.setupSdkPaths(sdk)
  return PyResult.success(sdk)
}

private const val PRINT_SYS_EXECUTABLE_SCRIPT = "import sys; print(sys.executable)"

/**
 * @return path to Conda interpreter binary on target
 */
private suspend fun getCondaPythonBinaryPath(
  condaEnv: PyCondaEnv,
  targetConfig: TargetEnvironmentConfiguration?,
): PyResult<FullPathOnTarget> {
  val binaryToExec = when (targetConfig) {
    null -> BinOnEel(Path(condaEnv.fullCondaPathOnTarget))
    else -> BinOnTarget(condaEnv.fullCondaPathOnTarget, targetConfig)
  }
  return CondaExecutor.runPythonInCondaEnv(
    binaryToExec, condaEnv.envIdentity, "-c", PRINT_SYS_EXECUTABLE_SCRIPT
  ).mapSuccess { it.trim() }
}

/**
 * See `com.jetbrains.env.python.conda.PyCondaSdkTest`
 */
internal suspend fun PyCondaCommand.createCondaSdkAlongWithNewEnv(
  newCondaEnvInfo: NewCondaEnvRequest,
  existingSdks: List<Sdk>,
): PyResult<Sdk> {
  PyCondaEnv.createEnv(this, newCondaEnvInfo).getOr { return it }
  val sdk = createCondaSdkFromExistingEnvironment(
    condaIdentity = newCondaEnvInfo.toIdentity(),
    existingSdks = existingSdks,
  ).getOr { return it }
  if (targetConfig == null) {
    saveLocalPythonCondaPath(Path.of(this@createCondaSdkAlongWithNewEnv.fullCondaPathOnTarget))
  }

  return PyResult.success(sdk)
}

private fun NewCondaEnvRequest.toIdentity(): PyCondaEnvIdentity =
  when (this) {
    is NewCondaEnvRequest.EmptyNamedEnv, is NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile -> PyCondaEnvIdentity.NamedEnv(envName)
    is NewCondaEnvRequest.EmptyUnnamedEnv -> PyCondaEnvIdentity.UnnamedEnv(envPath = envName, isBase = false)
  }

/**
 * Detects conda binary in PATH and then well-known locations on the local machine.
 */
internal suspend fun findCondaLocal(filter: (PathHolder.Eel) -> Boolean = { true }): PathHolder.Eel? = findConda(EelFileSystem(localEel), filter)

/**
 * Detects conda binary in PATH and then well-known locations on the provided FileSystem
 */
internal suspend fun <P : PathHolder> findConda(fileSystem: FileSystem<P>, filter: (P) -> Boolean = { true }): P? =
  CONDA_TOOL.getToolExecutable(fileSystem, null, filter)
