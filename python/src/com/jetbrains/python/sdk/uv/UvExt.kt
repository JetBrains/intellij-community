// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.util.progress.withProgressText
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.onFailure
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.TargetFileSystem
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.nio.file.Path
import kotlin.io.path.exists


internal val Sdk.isUv: Boolean
  get() = PythonSdkUtil.isPythonSdk(this) && uvFlavorData != null

internal val Sdk.uvFlavorData: UvSdkFlavorData?
  get() {
    return when (val data = pySdkAdditionalData) {
      is UvSdkAdditionalData -> data.flavorData
      is PyTargetAwareAdditionalData -> data.flavorAndData.data as? UvSdkFlavorData
      else -> null
    }
  }

internal val Sdk.uvUsePackageManagement: Boolean
  get() = PythonSdkUtil.isPythonSdk(this) && uvFlavorData?.usePip == true

/**
 * Execution context for UV SDK operations.
 * Consolidates all PathHolder type-specific data needed to execute UV commands.
 * 
 * Use [getUvExecutionContext] to create an instance from an SDK.
 */
internal sealed interface UvExecutionContext<P : PathHolder> {
  val workingDir: Path
  val venvPath: P?
  val fileSystem: FileSystem<P>
  val uvPath: P?

  data class Eel(
    override val workingDir: Path,
    override val venvPath: PathHolder.Eel?,
    override val fileSystem: EelFileSystem,
    override val uvPath: PathHolder.Eel?,
  ) : UvExecutionContext<PathHolder.Eel>

  data class Target(
    override val workingDir: Path,
    override val venvPath: PathHolder.Target?,
    override val fileSystem: TargetFileSystem,
    override val uvPath: PathHolder.Target?,
  ) : UvExecutionContext<PathHolder.Target>

  suspend fun createUvCli(): PyResult<UvLowLevel<P>> = createUvCli(uvPath, fileSystem).mapSuccess { uvCli ->
    createUvLowLevel(workingDir, uvCli, fileSystem, venvPath)
  }
}

private suspend fun createEelUvExecutionContext(
  workingDir: Path,
  venvPathString: String?,
  uvPathString: String?,
): UvExecutionContext.Eel {
  val eelApi = workingDir.getEelDescriptor().toEelApi()
  val fileSystem = EelFileSystem(eelApi)
  val uvPath = getUvExecutable(fileSystem, uvPathString)
  return UvExecutionContext.Eel(
    workingDir = workingDir,
    venvPath = venvPathString?.let { PathHolder.Eel(Path.of(it)) },
    fileSystem = fileSystem,
    uvPath = uvPath
  )
}

private suspend fun createTargetUvExecutionContext(
  workingDir: Path,
  venvPathString: FullPathOnTarget?,
  uvPathString: FullPathOnTarget?,
  targetConfig: TargetEnvironmentConfiguration,
): UvExecutionContext.Target {
  val fileSystem = TargetFileSystem(targetConfig, PythonLanguageRuntimeConfiguration())
  val uvPath = getUvExecutable(fileSystem, uvPathString)
  return UvExecutionContext.Target(
    workingDir = workingDir,
    venvPath = venvPathString?.let { PathHolder.Target(it) },
    fileSystem = fileSystem,
    uvPath = uvPath
  )
}

internal fun Sdk.getUvExecutionContextAsync(scope: CoroutineScope, project: Project? = null): Deferred<UvExecutionContext<*>>? {
  val data = sdkAdditionalData
  val uvWorkingDirectory = uvFlavorData?.uvWorkingDirectory
  val venvPathString = uvFlavorData?.venvPath
  val uvPathString = uvFlavorData?.uvPath

  return when (data) {
    is UvSdkAdditionalData -> {
      val defaultWorkingDir = project?.basePath?.let { Path.of(it) }
      val cwd = uvWorkingDirectory ?: defaultWorkingDir ?: return null
      scope.async(start = CoroutineStart.LAZY) {
        createEelUvExecutionContext(cwd, venvPathString, uvPathString)
      }
    }
    is PyTargetAwareAdditionalData -> {
      val targetConfig = data.targetEnvironmentConfiguration ?: return null
      val cwd = uvWorkingDirectory ?: return null
      scope.async(start = CoroutineStart.LAZY) {
        createTargetUvExecutionContext(cwd, venvPathString, uvPathString, targetConfig)
      }
    }
    else -> null
  }
}

@Service
private class MyService(val coroutineScope: CoroutineScope)

/**
 * Creates a [UvExecutionContext] from an SDK.
 * This factory consolidates all PathHolder casts in one place for SDK consumption code.
 * 
 * @param project Optional project for fallback working directory
 * @return UvExecutionContext if the SDK is a valid UV SDK, null otherwise
 */
internal suspend fun Sdk.getUvExecutionContext(project: Project? = null): UvExecutionContext<*>? =
  getUvExecutionContextAsync(service<MyService>().coroutineScope, project)?.await()

internal suspend fun setupNewUvSdkAndEnv(uvExecutable: Path, workingDir: Path, version: Version?, errorSink: ErrorSink): PyResult<Sdk> =
  setupNewUvSdkAndEnv(
    uvExecutable = PathHolder.Eel(uvExecutable),
    workingDir = workingDir,
    venvPath = null,
    fileSystem = EelFileSystem(localEel),
    version = version,
    errorSink = errorSink,
  )

internal suspend fun <P : PathHolder> setupNewUvSdkAndEnv(
  uvExecutable: P,
  workingDir: Path,
  venvPath: P?,
  fileSystem: FileSystem<P>,
  version: Version?,
  errorSink: ErrorSink,
  overrideExistingEnv: Boolean = false,
): PyResult<Sdk> {
  val shouldInitProject = !workingDir.resolve(PY_PROJECT_TOML).exists()
  val normalizedUvExecutablePath = fileSystem.normalizePathToRemote(uvExecutable)

  val uv = createUvLowLevel(workingDir, createUvCli(normalizedUvExecutablePath, fileSystem).getOr { return it }, fileSystem, venvPath)
  val pythonBinary = withProgressText(PyBundle.message("python.sdk.progress.uv.creating")) {
    uv.initializeEnvironment(shouldInitProject, version, clearExisting = overrideExistingEnv)
  }.getOr { return it }

  val sdk = setupExistingEnvAndSdk(
    pythonBinary = pythonBinary,
    uvPath = normalizedUvExecutablePath,
    workingDir = workingDir,
    fileSystem = fileSystem,
    usePip = false
  ).getOr { return it }

  if (!shouldInitProject) {
    uv.sync()
      .onFailure { errorSink.emit(it) }
      .onSuccess { SaveAndSyncHandler.getInstance().scheduleRefresh() }
  }

  return PyResult.success(sdk)
}

internal suspend fun setupExistingEnvAndSdk(
  pythonBinary: PythonBinary,
  uvPath: Path,
  envWorkingDir: Path,
  usePip: Boolean,
): PyResult<Sdk> =
  setupExistingEnvAndSdk(
    pythonBinary = PathHolder.Eel(pythonBinary),
    uvPath = PathHolder.Eel(uvPath),
    workingDir = envWorkingDir,
    fileSystem = EelFileSystem(localEel),
    usePip = usePip
  )

internal suspend fun <P : PathHolder> setupExistingEnvAndSdk(
  pythonBinary: P,
  uvPath: P,
  workingDir: Path,
  fileSystem: FileSystem<P>,
  usePip: Boolean,
): PyResult<Sdk> = withProgressText(PyBundle.message("python.sdk.progress.uv.configuring")) {
  val sdkAdditionalData = UvSdkAdditionalData(workingDir, usePip, fileSystem.resolvePythonHome(pythonBinary).toString(), uvPath.toString())
  val sdk = fileSystem.setupSdk(null, pythonBinary, sdkAdditionalData, null, null)
  sdk
}
