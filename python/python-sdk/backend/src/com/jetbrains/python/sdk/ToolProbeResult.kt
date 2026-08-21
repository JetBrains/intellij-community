// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jetbrains.annotations.ApiStatus

/**
 * One tool probe's outcome: where the executable was found, and what it printed for its version.
 *
 * Stays in this module — unlike [com.intellij.python.pytools.ToolCommandSpec], which describes how to *look* for a tool
 * — because it is parameterized by [PathHolder], the path abstraction `FileSystem` is built on.
 */
@ApiStatus.Internal
@PyInternalExecApi
data class ToolProbeResult<P : PathHolder>(
  val path: P,
  val versionOutput: String?,
)
