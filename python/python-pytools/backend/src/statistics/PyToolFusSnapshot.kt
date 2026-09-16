// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend.statistics

/**
 * Tool-supplied configuration snapshot consumed by `PyToolUsagesCollector` when it emits the `configuration.changed` FUS event.
 * Each [com.intellij.python.pytools.backend.PyTool] populates every field it owns.
 * Fields that the tool does not own stay null and are reported as `UNSURE`.
 *
 * The default [com.intellij.python.pytools.backend.PyTool.configurationFusSnapshot] fills [enabled] and [customPath].
 * This data is enough for tools without LSP feature flags.
 * LSP-backed tools override and `copy(...)` the default to add their feature fields.
 */
data class PyToolFusSnapshot(
  val enabled: Boolean,
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
