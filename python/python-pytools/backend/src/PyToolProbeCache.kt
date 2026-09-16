// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Short-TTL cache of the two process-backed answers a tool state needs: the managed-tool listing of an Eel
 * machine, and the version of one tool executable.
 *
 * Both are expensive. [GenericPyToolManager.list] runs `uv tool list --show-paths` and `uv tool list --outdated`,
 * and the second one reaches the package repository. A version costs a `<path> --version` run. The settings pages
 * ask for a tool state when a page opens, when a row expands, and after every install, so each answer is fetched
 * once and shared instead of on every request.
 *
 * Callers that change what an answer would be — installing or upgrading a tool — call [invalidate], so the change
 * is seen at once instead of after the TTL. Mirrors [PyExecutableCache], including its TTL, and is a hidden
 * application service: obtain the instance through [getInstance].
 */
@ApiStatus.Internal
interface PyToolProbeCache {
  /** The managed tools installed on [eel]'s machine. Empty when none is installed. */
  suspend fun listing(eel: EelApi): Map<PyTool<*>, InstalledInfo>

  /**
   * The version [tool] reports at [path] on [eelDescriptor]'s machine, from the cache or by running the tool now.
   *
   * Racing callers share one run. A `null` answer is cached too, so a binary that reports no usable version is
   * not re-run on every repaint. This is the version of an already-resolved executable; validating a path the
   * user just typed stays uncached, because the answer is about the file as it is right now.
   */
  suspend fun version(eelDescriptor: EelDescriptor, tool: PyTool<*>, path: Path): Version?

  /** Drop the cached listing and every cached version of [eelDescriptor]'s machine (no-op if unresolved). */
  fun invalidate(eelDescriptor: EelDescriptor)

  companion object {
    fun getInstance(): PyToolProbeCache = service()
  }
}
