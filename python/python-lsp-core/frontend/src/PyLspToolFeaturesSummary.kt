// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.frontend

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.lsp.core.common.PyLspToolConfigurationDto
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle

/**
 * Shared comma-separated summary of the standard LSP feature toggles, for `PyTool.summaryFor`.
 * Tools with extra toggles (e.g. Ruff) build their own.
 */
internal fun pyLspToolFeaturesSummary(
  settings: PyLspToolConfigurationDto,
  tool: LspPyToolFrontend,
): @NlsSafe String = buildList {
  if (settings.inspections) add(PyToolsUiBundle.message("checkbox.inspections"))
  if (settings.formatting == true) tool.formattingLabel?.let(::add)
  if (settings.sortImports == true) tool.sortImportsLabel?.let(::add)
  if (settings.completions == true) add(PyToolsUiBundle.message("checkbox.completions"))
  if (settings.inlayHints == true) add(PyToolsUiBundle.message("checkbox.inlay.hints"))
  if (settings.documentation == true) add(PyToolsUiBundle.message("checkbox.documentation"))
}.joinToString(", ")
