// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.util.progress.withProgressText
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.community.impl.uv.common.UV_UI_INFO
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.onFailure
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.DetectedSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pyvenvContains
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
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
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
  val fileSystem: FileSystem<P>

  /**
   * Creates SDK additional data appropriate for this context type.
   */
  fun createSdkAdditionalData(
    workingDir: Path,
    pythonBinary: P,
    usePip: Boolean,
    uvPath: P,
  ): PythonSdkAdditionalData

  /**
   * Maps a path using target VFS mapper if needed.
   */
  fun mapProbablyWslPath(path: P): P

  /**
   * Suggests SDK name appropriate for this context type.
   */
  fun suggestSdkName(sdkAdditionalData: PythonSdkAdditionalData): String

  /**
   * Detects UV environments in immediate subdirectories of [workingDir].
   */
  suspend fun detectEnvironments(): List<DetectedSelectableInterpreter<P>>

  class Eel(
    override val workingDir: Path,
    override val fileSystem: FileSystem.Eel,
  ) : UvPathOperations<PathHolder.Eel> {
    override fun createSdkAdditionalData(
      workingDir: Path,
      pythonBinary: PathHolder.Eel,
      usePip: Boolean,
      uvPath: PathHolder.Eel,
    ): PythonSdkAdditionalData {
      val venvPath = fileSystem.resolvePythonHome(pythonBinary)
      return UvSdkAdditionalData(workingDir, usePip, venvPath.path, uvPath.path)
    }

    override fun suggestSdkName(sdkAdditionalData: PythonSdkAdditionalData): String {
      return "uv (${PathUtil.getFileName(workingDir.pathString)})"
    }

    override suspend fun detectEnvironments(): List<DetectedSelectableInterpreter<PathHolder.Eel>> {
      if (workingDir.getEelDescriptor().toEelApi() != fileSystem.eelApi) return emptyList()

      return workingDir.listDirectoryEntries().filter { it.isDirectory() }.mapNotNull { possibleVenvHome ->
        val pythonBinary = possibleVenvHome.resolvePythonBinary() ?: return@mapNotNull null
        val pythonInfo = pythonBinary.validatePythonAndGetInfo().successOrNull ?: return@mapNotNull null
        val ui = if (pythonBinary.pyvenvContains("uv = ")) UV_UI_INFO else null
        DetectedSelectableInterpreter(PathHolder.Eel(pythonBinary), pythonInfo, false, ui)
      }
    }

    override fun mapProbablyWslPath(path: PathHolder.Eel): PathHolder.Eel = path
  }

  class Target(
    override val workingDir: Path,
    override val fileSystem: FileSystem.Target,
  ) : UvPathOperations<PathHolder.Target> {
    override fun createSdkAdditionalData(
      workingDir: Path,
      pythonBinary: PathHolder.Target,
      usePip: Boolean,
      uvPath: PathHolder.Target,
    ): PythonSdkAdditionalData {
      val venvPath = fileSystem.resolvePythonHome(pythonBinary)
      val targetConfig = fileSystem.targetEnvironmentConfiguration
      val flavorAndData = PyFlavorAndData(UvSdkFlavorData(workingDir, usePip.takeIf { it }, venvPath.pathString, uvPath.pathString), UvSdkFlavor)
      return PyTargetAwareAdditionalData(flavorAndData, targetConfig).also {
        it.interpreterPath = pythonBinary.pathString
      }
    }

    override fun suggestSdkName(sdkAdditionalData: PythonSdkAdditionalData): String {
      val baseName = PythonInterpreterTargetEnvironmentFactory.findDefaultSdkName(null, sdkAdditionalData as PyTargetAwareAdditionalData, null)
      return "uv $baseName"
    }

    // TODO PY-87712 Support detection for remotes
    override suspend fun detectEnvironments(): List<DetectedSelectableInterpreter<PathHolder.Target>> {
      return emptyList()
    }

    override fun mapProbablyWslPath(path: PathHolder.Target): PathHolder.Target {
      val targetConfig = fileSystem.targetEnvironmentConfiguration
      val mapper = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(targetConfig)
      val targetPath = mapper?.getTargetPath(Path.of(path.pathString)) ?: path.pathString
      return PathHolder.Target(targetPath)
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
  fileSystem: FileSystem<P>,
): UvPathOperations<P> {
  return when (fileSystem) {
    is FileSystem.Eel -> UvPathOperations.Eel(
      workingDir = workingDir,
      fileSystem = fileSystem,
    ) as UvPathOperations<P>
    is FileSystem.Target -> UvPathOperations.Target(
      workingDir = workingDir,
      fileSystem = fileSystem,
    ) as UvPathOperations<P>
  }
}

private suspend fun createEelUvExecutionContext(
  workingDir: Path,
  venvPathString: String?,
  uvPathString: String?,
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
  targetConfig: TargetEnvironmentConfiguration,
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
  val ops = createUvPathOperations(workingDir, fileSystem)

  val shouldInitProject = !workingDir.resolve(PY_PROJECT_TOML).exists()
  val mappedUvExecutable = ops.mapProbablyWslPath(uvExecutable)

  val uv = createUvLowLevel(workingDir, createUvCli(mappedUvExecutable, fileSystem).getOr { return it }, fileSystem, venvPath)
  val pythonBinary = withProgressText(PyBundle.message("python.sdk.progress.uv.creating")) {
    uv.initializeEnvironment(shouldInitProject, version)
  }.getOr { return it }

  val sdk = setupExistingEnvAndSdk(
    pythonBinary = pythonBinary,
    uvPath = mappedUvExecutable,
    workingDir = workingDir,
    fileSystem = fileSystem,
    usePip = false
  ).getOr { return it }

  if (!shouldInitProject) {
    uv.sync().onFailure { errorSink.emit(it) }
  }

  return PyResult.success(sdk)
}

suspend fun setupExistingEnvAndSdk(
  pythonBinary: PythonBinary,
  uvPath: Path,
  envWorkingDir: Path,
  usePip: Boolean,
): PyResult<Sdk> =
  setupExistingEnvAndSdk(
    pythonBinary = PathHolder.Eel(pythonBinary),
    uvPath = PathHolder.Eel(uvPath),
    workingDir = envWorkingDir,
    fileSystem = FileSystem.Eel(localEel),
    usePip = usePip
  )

suspend fun <P : PathHolder> setupExistingEnvAndSdk(
  pythonBinary: P,
  uvPath: P,
  workingDir: Path,
  fileSystem: FileSystem<P>,
  usePip: Boolean,
): PyResult<Sdk> = withProgressText(PyBundle.message("python.sdk.progress.uv.configuring")) {
  val ops = createUvPathOperations(workingDir, fileSystem)
  val sdkAdditionalData = ops.createSdkAdditionalData(workingDir, pythonBinary, usePip, uvPath)
  val sdkName = ops.suggestSdkName(sdkAdditionalData)
  val sdk = createSdk(pythonBinary, sdkName, sdkAdditionalData)
  sdk
}
