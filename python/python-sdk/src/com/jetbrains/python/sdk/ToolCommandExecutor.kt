// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isSuccess
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.impl.PySdkBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * "implementation" for pip and poetry tools.
 * [toolName] (i.e `poetry`).
 * [additionalSearchPaths] additional paths to look for [toolName]
 */
@ApiStatus.Internal
@PyInternalExecApi
data class ToolCommandExecutor(
  private val toolName: @NlsSafe String,
  private val additionalSearchPaths: List<ToolSearchPath> = emptyList(),
  private val getToolPathFromSettings: PropertiesComponent.() -> @SystemIndependent String?,
) {
  companion object {
    private val KNOWN_SEARCH_PATHS = listOf(
      ToolSearchPath.RelativePathFromHome(listOf(".local", "bin")),
      ToolSearchPath.RelativePath("APPDATA", listOf("Python", "Scripts"), Platform.WINDOWS),
      ToolSearchPath.RelativePath("LOCALAPPDATA", listOf("Python", "Scripts"), Platform.WINDOWS),
    )
  }

  suspend fun <P : PathHolder> detectToolExecutable(
    fileSystem: FileSystem<P>,
    filter: (P) -> Boolean,
  ): P? {
    val toolSpec = toCommandSpec()
    val resolvedSearchPaths = fileSystem.resolveToolSearchPaths(toolSpec)
    return fileSystem.detectTool(toolSpec.toolName, resolvedSearchPaths, filter)
  }

  fun toCommandSpec(): ToolCommandSpec = ToolCommandSpec(toolName, KNOWN_SEARCH_PATHS + additionalSearchPaths)

  suspend fun <P : PathHolder> getToolExecutable(
    fileSystem: FileSystem<P>,
    pathFromSdk: FullPathOnTarget?,
    filter: (P) -> Boolean = { true },
  ): P? {
    val fromSdk = pathFromSdk?.let { fileSystem.parsePath(it).successOrNull }?.takeIf(filter)
    if (fromSdk != null) return fromSdk

    val toolPathFromSettings = if (fileSystem.isLocal) getToolPathFromSettings(PropertiesComponent.getInstance())?.let {
      fileSystem.getValidExecutableOrNull(it, filter)
    }
    else null
    if (toolPathFromSettings != null) return toolPathFromSettings
    return detectToolExecutable(fileSystem, filter)
  }

  /**
   * Same as [getToolExecutable] but returns user-reable error if tool can't be found
   */
  suspend fun <P : PathHolder> getToolExecutableOrError(
    fileSystem: FileSystem<P>,
    pathFromSdk: FullPathOnTarget?,
    filter: (P) -> Boolean = { true },
  ): Result<P, MessageError> = getToolExecutable(fileSystem, pathFromSdk, filter)?.let { PyResult.success(it) }
                               ?: PyResult.localizedError(PySdkBundle.message("path.validation.file.not.found", toolName))

  suspend fun <P : PathHolder, T> runTool(
    fileSystem: FileSystem<P>,
    pathFromSdk: FullPathOnTarget?,
    dirPath: Path?,
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    uploadConfig: UploadConfig? = null,
    downloadConfig: DownloadConfig? = null,
    transformer: ProcessOutputTransformer<T>,
  ): PyResult<T> {
    val executable = getToolExecutable(fileSystem, pathFromSdk)
                     ?: return PyResult.localizedError(PySdkBundle.message("cannot.find.executable", toolName, fileSystem.userReadableName))
    val bin = fileSystem.getBinaryToExec(executable, dirPath)
    return runExecutableWithProgress(
      binaryToExec = bin,
      timeout = 10.minutes,
      env = env,
      args = args,
      uploadConfig = uploadConfig,
      downloadConfig = downloadConfig,
      transformer = transformer,
    )
  }
}

private suspend fun <P : PathHolder> FileSystem<P>.getValidExecutableOrNull(path: FullPathOnTarget, filter: (P) -> Boolean): P? =
  parsePath(path).successOrNull?.takeIf { validateExecutable(it).isSuccess && filter(it) }

@ApiStatus.Internal
suspend fun <P : PathHolder> ToolCommandExecutor.runTool(
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget?,
  dirPath: Path?,
  vararg args: String,
  env: Map<String, String> = emptyMap(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
): PyResult<String> =
  runTool(
    fileSystem,
    pathFromSdk,
    dirPath,
    args = args,
    env = env,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
    transformer = ZeroCodeStdoutTransformer,
  )
