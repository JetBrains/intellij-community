// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@PyInternalExecApi
data class ToolCommandSpec(
  val toolName: @NlsSafe String,
  val searchPaths: List<ToolSearchPath>,
) {
  fun searchPathsFor(platform: Platform): List<ToolSearchPath> {
    return searchPaths.distinct().filter { it.platform == null || it.platform == platform }
  }
}

@ApiStatus.Internal
@PyInternalExecApi
data class ToolProbeResult<P : PathHolder>(
  val path: P,
  val versionOutput: String?,
)

@ApiStatus.Internal
@PyInternalExecApi
suspend fun <P : PathHolder> FileSystem<P>.resolveToolSearchPaths(toolSpec: ToolCommandSpec): List<P> {
  return toolSpec.searchPathsFor(platformAndRoot.platform).mapNotNull { searchPath ->
    when (searchPath) {
      is ToolSearchPath.AbsolutePath -> parsePath(searchPath.path).successOrNull
      is ToolSearchPath.RelativePath -> getFullPath(searchPath.prefixEnvVar, searchPath.pathComponents)
      is ToolSearchPath.RelativePathFromHome -> getFullPathFromHome(searchPath.pathComponents)
    }
  }
}

/**
 * Represents a location to search for a tool executable.
 *
 * @property platform The platform supported by this location, or `null` when it applies to every platform.
 */
@ApiStatus.Internal
@PyInternalExecApi
sealed interface ToolSearchPath {
  val platform: Platform?

  data class RelativePathFromHome(val pathComponents: List<String>, override val platform: Platform? = null) : ToolSearchPath
  data class RelativePath(
    val prefixEnvVar: String, val pathComponents: List<String>, override val platform: Platform? = null,
  ) : ToolSearchPath

  data class AbsolutePath(val path: FullPathOnTarget, override val platform: Platform? = null) : ToolSearchPath
}
