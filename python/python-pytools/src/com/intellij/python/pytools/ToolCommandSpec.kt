// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyInternalExecApi
import org.jetbrains.annotations.ApiStatus

/**
 * How to find one tool executable: its command [toolName] plus the directories to search beyond `PATH`.
 *
 * Lives here rather than in `python-sdk` because this module owns the semantics — [PyExecutable.toolCommandSpec] is a
 * member of this module's interface, and [pyExecutableSpec] is what builds a spec — while `python-sdk` only consumes one
 * (`FileSystem.detectTool`, `FileSystem.probeTools`). Detection is what pytools is for; an SDK is not involved.
 */
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
