// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyStepIntoSupport {
  /** True when stepping into user code only is currently possible. */
  val isStepIntoMyCodeAvailable: Boolean

  /** Localized tooltip explaining why stepIntoMyCode is unavailable (null if available). */
  val stepIntoMyCodeUnavailableReason: @NlsContexts.Tooltip String?

  /** Execute a "step into my code" for the current suspend context. */
  fun performStepIntoMyCode()

  /**
   * True when stepping into all code (including libraries) is available.
   * Defaults to true; override to false when the session was launched with justMyCode=true.
   */
  val isStepIntoAllCodeAvailable: Boolean get() = true

  /**
   * Localized tooltip explaining why stepIntoAllCode is unavailable (null if available or not applicable).
   * Shown in the toolbar tooltip when [isStepIntoAllCodeAvailable] is false.
   */
  val stepIntoAllCodeUnavailableReason: @NlsContexts.Tooltip String? get() = null

  /** Execute a "step into all code" (including libraries) for the current suspend context. */
  fun performStepIntoAllCode() {}

  /**
   * Applies [enableJustMyCode] to the active run configuration and opens the run configuration editor
   * so the user can verify and then restart the session manually.
   * Default is a no-op (used by pydevd, which does not have a justMyCode session-level flag).
   */
  fun applyJustMyCodeChange(enableJustMyCode: Boolean) {}
}
