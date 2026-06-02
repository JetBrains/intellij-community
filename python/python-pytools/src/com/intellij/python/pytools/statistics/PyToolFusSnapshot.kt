// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.statistics

import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode

/**
 * Tool-supplied configuration snapshot consumed by `PyToolUsagesCollector` when it emits the
 * `configuration.changed` FUS event. Each [com.intellij.python.pytools.PyTool] populates every field it actually owns;
 * fields the tool does not own stay null and are reported as `UNSURE`.
 *
 * The default [com.intellij.python.pytools.PyTool.configurationFusSnapshot] returns a snapshot with [enabled],
 * [executableDiscoveryMode], and [customPath] filled in — enough for tools without LSP-style feature flags.
 * LSP-backed tools override and `copy(...)` the default to add their feature fields.
 */
data class PyToolFusSnapshot(
  val enabled: Boolean,
  val executableDiscoveryMode: ExecutableDiscoveryMode,
  /**
   * True when the tool's executable is overridden by a user-supplied custom path
   * (set via "Browse for executable" in the External Tools settings).
   */
  val customPath: Boolean = false,
  val inspections: Boolean? = null,
  val completions: Boolean? = null,
  val inlayHints: Boolean? = null,
  val documentation: Boolean? = null,
  val formatting: Boolean? = null,
  val sortImports: Boolean? = null,
)
