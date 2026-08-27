// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Single source of truth for resolving a [PyExecutable]'s executable path on a given Eel machine: the
 * user-chosen custom path if set, otherwise short-TTL-cached auto-detection. A `null` result means
 * neither a custom path nor detection found the executable.
 *
 * Callers that change what detection would find (installing a tool, or writing a custom path) call
 * [invalidate] so the change is seen immediately instead of after the TTL. The implementation is a
 * hidden application service — obtain the instance via [getInstance].
 */
@ApiStatus.Internal
interface PyExecutableCache {
  /**
   * The resolved executable path for [executable] on [eelDescriptor]'s machine, or `null` if not found.
   *
   * The path is always one that exists right now. A tool removed from the machine leaves a stale answer behind, in
   * the custom-path store and in the detection cache alike, and neither is handed out.
   */
  suspend fun get(eelDescriptor: EelDescriptor, executable: PyExecutable): Path?

  /** Drop the cached detection for [executable] on [eelDescriptor]'s machine (no-op if unresolved). */
  fun invalidate(eelDescriptor: EelDescriptor, executable: PyExecutable)

  companion object {
    fun getInstance(): PyExecutableCache = service()
  }
}
