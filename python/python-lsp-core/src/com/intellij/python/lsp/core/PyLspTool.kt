// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.pytools.backend.statistics.PyToolFusSnapshot
import com.intellij.python.lsp.core.common.PyLspToolConfigurationDto
import javax.swing.Icon

/**
 * Base for every LSP-backed [PyTool]. Captures the shared, non-UI wiring around a per-project
 * [PyLspToolConfiguration] — its [configuration] service is the single source of the tool's settings,
 * used here for legacy-state migration and the FUS snapshot, and by the detail UI in the UI module.
 *
 * UI concerns (the detail configurable, the features summary) deliberately live in the UI layer and
 * are not part of this base.
 */
abstract class PyLspTool<C : PyLspToolConfiguration<*>> : PyTool<PyLspToolConfigurationDto> {
  abstract val lspServerName: @NlsSafe String

  /**
   * The tool icon that the Language Services widget shows.
   *
   * The widget item comes from [PyLspToolIntegrationProvider], which runs where the server runs, so the icon
   * belongs here and not to the frontend tool.
   */
  abstract val icon: Icon

  /** Per-project settings service backing this tool — the single source of its configuration. */
  abstract fun configuration(project: Project): C

  override fun migrateLegacyState(project: Project): PyToolsState.ToolEntry =
    PyToolsState.ToolEntry(configuration(project).migrateToPyToolState())

  override fun configurationFusSnapshot(project: Project): PyToolFusSnapshot {
    val cfg = configuration(project)
    return super.configurationFusSnapshot(project).copy(
      inspections = cfg.inspections,
      completions = cfg.completions,
      inlayHints = cfg.inlayHints,
      documentation = cfg.documentation,
    )
  }

  override fun configurationState(project: Project): PyLspToolConfigurationDto {
    val cfg = configuration(project)
    return PyLspToolConfigurationDto(
      inspections = cfg.inspections,
      completions = cfg.completions,
      inlayHints = cfg.inlayHints,
      documentation = cfg.documentation,
    )
  }

  override fun applyConfigurationState(project: Project, state: PyLspToolConfigurationDto) {
    val cfg = configuration(project)
    cfg.inspections = state.inspections
    cfg.completions = state.completions
    cfg.inlayHints = state.inlayHints
    cfg.documentation = state.documentation
  }
}
