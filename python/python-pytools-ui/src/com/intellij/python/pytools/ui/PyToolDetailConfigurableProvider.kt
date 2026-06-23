// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui

import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pytools.lsp.PyLspToolSettings

/**
 * Implemented by a `com.intellij.python.pytools.PyTool` that contributes a detail panel to the
 * External Tools table's Edit dialog. Kept out of `PyTool` itself so the core tool abstraction stays
 * free of UI types; the table discovers it with `tool as? PyToolDetailConfigurableProvider`.
 */
interface PyToolDetailConfigurableProvider {
  fun createConfigurable(project: Project): UnnamedConfigurable
}

/**
 * Shared comma-separated summary of the standard LSP feature toggles, for `PyTool.summaryFor`.
 * Tools with extra toggles (e.g. Ruff) build their own.
 */
fun pyLspToolFeaturesSummary(settings: PyLspToolSettings): @NlsSafe String = buildList {
  if (settings.inspections) add(PyToolsUiBundle.message("checkbox.inspections"))
  if (settings.completions == true) add(PyToolsUiBundle.message("checkbox.completions"))
  if (settings.inlayHints == true) add(PyToolsUiBundle.message("checkbox.inlay.hints"))
  if (settings.documentation == true) add(PyToolsUiBundle.message("checkbox.documentation"))
}.joinToString(", ")
