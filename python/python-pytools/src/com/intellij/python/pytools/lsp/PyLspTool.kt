// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.lsp

import com.intellij.openapi.project.Project
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.statistics.PyToolFusSnapshot
import javax.swing.Icon

/**
 * Base for every LSP-backed [PyTool]. Captures the shared, non-UI wiring around a per-project
 * [PyLspToolConfiguration] — its [configuration] service is the single source of the tool's settings,
 * used here for legacy-state migration and the FUS snapshot, and by the detail UI in the UI module.
 *
 * UI concerns (the detail configurable, the features summary) deliberately live in the UI layer and
 * are not part of this base.
 */
abstract class PyLspTool<C : PyLspToolConfiguration<*>> : PyTool {
  /** Per-project settings service backing this tool — the single source of its configuration. */
  abstract fun configuration(project: Project): C

  /** Icon shown for this tool's LSP server (status-bar widget, advertiser notification, ...). */
  abstract val icon: Icon

  override fun migrateLegacyState(project: Project): PyToolsState.ToolEntry = configuration(project).migrateToPyToolState()

  override fun configurationFusSnapshot(project: Project): PyToolFusSnapshot {
    val cfg = configuration(project)
    return super.configurationFusSnapshot(project).copy(
      inspections = cfg.inspections,
      completions = cfg.completions,
      inlayHints = cfg.inlayHints,
      documentation = cfg.documentation,
    )
  }
}
