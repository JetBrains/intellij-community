// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.util.PathUtil
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.onFailure
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.detectUvExecutable
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString


internal val Sdk.isUv: Boolean
  get() {
    if (!PythonSdkUtil.isPythonSdk(this)) {
      return false
    }
    return uvFlavorData != null
  }

internal val Sdk.uvFlavorData: UvSdkFlavorData?
  get() {
    return when (val data = getOrCreateAdditionalData()) {
      is UvSdkAdditionalData -> data.flavorData
      is PyTargetAwareAdditionalData -> data.flavorAndData.data as? UvSdkFlavorData
      else -> null
    }
  }

internal val Sdk.uvUsePackageManagement: Boolean
  get() {
    if (!PythonSdkUtil.isPythonSdk(this)) {
      return false
    }

    return uvFlavorData?.usePip == true
  }

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
    override val fileSystem: FileSystem.Eel,
    override val uvPath: PathHolder.Eel?,
  ) : UvExecutionContext<PathHolder.Eel>

  data class Target(
    override val workingDir: Path,
    override val venvPath: PathHolder.Target?,
    override val fileSystem: FileSystem.Target,
    override val uvPath: PathHolder.Target?,
  ) : UvExecutionContext<PathHolder.Target>

  suspend fun createUvCli(): PyResult<UvLowLevel<P>> = createUvCli(uvPath, fileSystem).mapSuccess { uvCli ->
    createUvLowLevel(workingDir, uvCli, fileSystem, venvPath)
  }
}

/**
 * Operations helper for UV SDK creation code.
 * Consolidates all PathHolder type-specific operations needed to create UV SDKs.
 *
 * Use [createUvPathOperations] factory to create an instance.
 */
sealed interface UvPathOperations<P : PathHolder> {
  val workingDir: Path
  val venvPath: P?
  val fileSystem: FileSystem<P>

  /**
   * Creates SDK additional data appropriate for this context type.
   */
  fun createSdkAdditionalData(
    workingDir: Path,
    venvPath: P?,
    usePip: Boolean,
    uvPath: P,
  ): PythonSdkAdditionalData

  /**
   * Maps a path using target VFS mapper if needed.
   */
  fun mapProbablyWslPath(path: P): P

  /**
   * Checks if pyproject.toml exists at the working directory.
   */
  suspend fun pyProjectTomlExists(): Boolean

  class Eel(
    override val workingDir: Path,
    override val venvPath: PathHolder.Eel?,
    override val fileSystem: FileSystem.Eel,
  ) : UvPathOperations<PathHolder.Eel> {
    override fun createSdkAdditionalData(
      workingDir: Path,
      venvPath: PathHolder.Eel?,
      usePip: Boolean,
      uvPath: PathHolder.Eel,
    ): PythonSdkAdditionalData {
      return UvSdkAdditionalData(workingDir, usePip, venvPath?.path, uvPath.path)
    }

    override fun mapProbablyWslPath(path: PathHolder.Eel): PathHolder.Eel = path

    override suspend fun pyProjectTomlExists(): Boolean {
      val toml = workingDir.resolve(PY_PROJECT_TOML)
      return toml.exists()
    }
  }

  class Target(
    override val workingDir: Path,
    override val venvPath: PathHolder.Target?,
    override val fileSystem: FileSystem.Target,
  ) : UvPathOperations<PathHolder.Target> {
    override fun createSdkAdditionalData(
      workingDir: Path,
      venvPath: PathHolder.Target?,
      usePip: Boolean,
      uvPath: PathHolder.Target,
    ): PythonSdkAdditionalData {
      val targetConfig = fileSystem.targetEnvironmentConfiguration
      val flavorAndData = PyFlavorAndData(UvSdkFlavorData(workingDir, usePip, venvPath?.pathString, uvPath.pathString), UvSdkFlavor)
      return PyTargetAwareAdditionalData(flavorAndData, targetConfig)
    }

    override fun mapProbablyWslPath(path: PathHolder.Target): PathHolder.Target {
      val targetConfig = fileSystem.targetEnvironmentConfiguration
      val mapper = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(targetConfig)
      val targetPath = mapper?.getTargetPath(Path.of(path.pathString)) ?: path.pathString
      return PathHolder.Target(targetPath)
    }

    override suspend fun pyProjectTomlExists(): Boolean {
      val targetConfig = fileSystem.targetEnvironmentConfiguration
      val mapper = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(targetConfig)
      val mappedPathString = mapper?.getTargetPath(workingDir) ?: workingDir.pathString
      val targetPath = constant(mappedPathString)
      val tomlPath = targetPath.getRelativeTargetPath(PY_PROJECT_TOML)
      val toml = tomlPath.apply(targetConfig.createEnvironmentRequest(project = null).prepareEnvironment(TargetProgressIndicator.EMPTY))
      return fileSystem.fileExists(PathHolder.Target(toml))
    }
  }
}

/**
 * Creates a [UvPathOperations] instance for the given parameters.
 * This factory consolidates the type dispatch in one place.
 */
// TODO PY-87712 Think about contracts
@Suppress("UNCHECKED_CAST")
internal fun <P : PathHolder> createUvPathOperations(
  workingDir: Path,
  venvPath: P?,
  fileSystem: FileSystem<P>,
): UvPathOperations<P> {
  return when (fileSystem) {
    is FileSystem.Eel -> UvPathOperations.Eel(
      workingDir = workingDir,
      venvPath = venvPath as? PathHolder.Eel,
      fileSystem = fileSystem,
    ) as UvPathOperations<P>
    is FileSystem.Target -> UvPathOperations.Target(
      workingDir = workingDir,
      venvPath = venvPath as? PathHolder.Target,
      fileSystem = fileSystem,
    ) as UvPathOperations<P>
  }
}

private suspend fun createEelUvExecutionContext(
  workingDir: Path,
  venvPathString: String?,
  uvPathString: String?
): UvExecutionContext.Eel {
  val eelApi = workingDir.getEelDescriptor().toEelApi()
  val fileSystem = FileSystem.Eel(eelApi)
  val uvPath = detectUvExecutable(fileSystem, uvPathString)
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
  targetConfig: TargetEnvironmentConfiguration
): UvExecutionContext.Target {
  val fileSystem = FileSystem.Target(targetConfig, PythonLanguageRuntimeConfiguration())
  val uvPath = detectUvExecutable(fileSystem, uvPathString)
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

suspend fun setupNewUvSdkAndEnv(uvExecutable: Path, workingDir: Path, version: Version?, errorSink: ErrorSink): PyResult<Sdk> =
  setupNewUvSdkAndEnv(
    uvExecutable = PathHolder.Eel(uvExecutable),
    workingDir = workingDir,
    venvPath = null,
    fileSystem = FileSystem.Eel(localEel),
    version = version,
    errorSink = errorSink,
  )

suspend fun <P : PathHolder> setupNewUvSdkAndEnv(
  uvExecutable: P,
  workingDir: Path,
  venvPath: P?,
  fileSystem: FileSystem<P>,
  version: Version?,
  errorSink: ErrorSink,
): PyResult<Sdk> {
  val ops = createUvPathOperations(workingDir, venvPath, fileSystem)

  val shouldInitProject = !ops.pyProjectTomlExists()
  val mappedUvExecutable = ops.mapProbablyWslPath(uvExecutable)

  val uv = createUvLowLevel(workingDir, createUvCli(mappedUvExecutable, fileSystem).getOr { return it }, fileSystem, venvPath)
  val pythonBinary = uv.initializeEnvironment(shouldInitProject, version).getOr { return it }

  val sdk = setupExistingEnvAndSdk(
    pythonBinary = pythonBinary,
    uvPath = mappedUvExecutable,
    workingDir = workingDir,
    venvPath = venvPath,
    fileSystem = fileSystem,
    usePip = false
  ).getOr { return it }

  if (!shouldInitProject) {
    uv.sync().onFailure { errorSink.emit(it) }
  }

  return PyResult.success(sdk)
}

suspend fun setupExistingEnvAndSdk(
  pythonBinary: Path,
  uvPath: Path,
  envWorkingDir: Path,
  usePip: Boolean,
): PyResult<Sdk> =
  setupExistingEnvAndSdk(
    pythonBinary = PathHolder.Eel(pythonBinary),
    uvPath = PathHolder.Eel(uvPath),
    workingDir = envWorkingDir,
    venvPath = null,
    fileSystem = FileSystem.Eel(localEel),
    usePip = usePip
  )

suspend fun <P : PathHolder> setupExistingEnvAndSdk(
  pythonBinary: P,
  uvPath: P,
  workingDir: Path,
  venvPath: P?,
  fileSystem: FileSystem<P>,
  usePip: Boolean,
): PyResult<Sdk> {
  val ops = createUvPathOperations(workingDir, venvPath, fileSystem)
  val sdkAdditionalData = ops.createSdkAdditionalData(workingDir, venvPath, usePip, uvPath)
  val sdkName = "uv (${PathUtil.getFileName(workingDir.pathString)})"
  val sdk = createSdk(pythonBinary, sdkName, sdkAdditionalData)
  return sdk
}
