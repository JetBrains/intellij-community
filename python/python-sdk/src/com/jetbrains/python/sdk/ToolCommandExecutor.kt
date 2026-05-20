// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
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
    val searchPaths = KNOWN_SEARCH_PATHS + additionalSearchPaths
    val resolvedSearchPaths = searchPaths.distinct().filter {
      it.platform == null || it.platform == fileSystem.platformAndRoot.platform
    }.mapNotNull { searchPath ->
      when (searchPath) {
        is ToolSearchPath.RelativePath -> fileSystem.getFullPath(searchPath.prefixEnvVar, searchPath.pathComponents)
        is ToolSearchPath.RelativePathFromHome -> fileSystem.getFullPathFromHome(searchPath.pathComponents)
        is ToolSearchPath.AbsolutePath -> fileSystem.parsePath(searchPath.path).successOrNull
      }
    }
    return fileSystem.detectTool(toolName, resolvedSearchPaths, filter)
  }

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

  suspend fun <P : PathHolder, T> runTool(
    fileSystem: FileSystem<P>,
    pathFromSdk: FullPathOnTarget?,
    dirPath: Path?,
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    transformer: ProcessOutputTransformer<T>,
  ): PyResult<T> {
    val executable = getToolExecutable(fileSystem, pathFromSdk)
                     ?: return PyResult.localizedError(PySdkBundle.message("cannot.find.executable", toolName, fileSystem.userReadableName))
    val bin = fileSystem.getBinaryToExec(executable, dirPath)
    return runExecutableWithProgress(bin, 10.minutes, env = env, args = args, transformer = transformer)
  }

  private suspend fun <P : PathHolder> FileSystem<P>.getValidExecutableOrNull(path: FullPathOnTarget, filter: (P) -> Boolean): P? =
    parsePath(path).successOrNull?.takeIf { validateExecutable(it).isSuccess && filter(it) }
}

@ApiStatus.Internal
suspend fun <P : PathHolder> ToolCommandExecutor.runTool(
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget?,
  dirPath: Path?,
  vararg args: String,
  env: Map<String, String> = emptyMap(),
): PyResult<String> =
  runTool(fileSystem, pathFromSdk, dirPath, args = args, env = env, transformer = ZeroCodeStdoutTransformer)

/**
 * Represents the search path for locating a specific tool.
 *
 * This interface and its implementations define various ways to specify the location of a tool, whether
 * through a relative path, an environment variable prefix, or an absolute path.
 *
 * @property platform An optional platform specification indicating the platform supported by the path (or null if it's common).
 */
@ApiStatus.Internal
sealed interface ToolSearchPath {
  val platform: Platform?

  data class RelativePathFromHome(val pathComponents: List<String>, override val platform: Platform? = null) : ToolSearchPath
  data class RelativePath(val prefixEnvVar: String, val pathComponents: List<String>, override val platform: Platform? = null) :
    ToolSearchPath

  data class AbsolutePath(val path: FullPathOnTarget, override val platform: Platform? = null) : ToolSearchPath
}
