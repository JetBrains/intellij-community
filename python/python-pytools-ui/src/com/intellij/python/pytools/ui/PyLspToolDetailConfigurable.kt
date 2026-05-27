// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.lsp.PyLspToolSettings
import com.intellij.python.pytools.statistics.PyToolActionSource
import com.intellij.python.pytools.statistics.PyToolUsagesCollector
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel

/**
 * Shared base for every LSP-backed tool's detail configurable. Subclasses supply only the
 * per-project [settings] service, an optional [inlayHintLabel] override (Pyright uses a
 * tool-specific string), and optional tool-specific rows via [extraRows] (Ruff uses this for
 * its `formatting` and `sortImports` checkboxes).
 *
 * Logging happens automatically in [apply] via [PyToolUsagesCollector.Helper.logConfigurationChanged],
 * which pulls field values from [PyTool.configurationFusSnapshot]. A new tool that extends this
 * base therefore cannot accidentally skip FUS reporting — there is no per-subclass `apply` to
 * forget.
 */
abstract class PyLspToolDetailConfigurable(
  protected val project: Project,
  private val tool: PyTool,
) : BoundConfigurable(tool.presentableName) {

  protected abstract val settings: PyLspToolSettings

  /** Label used for the inlay-hint row. Override when the tool needs a custom string. */
  protected open val inlayHintLabel: String = PyToolsUiBundle.message("checkbox.inlay.hints")

  /** Override to append tool-specific rows after the standard LSP feature block. */
  protected open fun Panel.extraRows() {}

  final override fun createPanel(): DialogPanel = panel {
    PyLspToolFeatureRows.build(
      panel = this,
      settings = settings,
      inlayHintLabel = inlayHintLabel,
      extra = { extraRows() },
    )
  }

  final override fun apply() {
    super.apply()
    PyToolUsagesCollector.Helper.logConfigurationChanged(
      project = project,
      tool = tool,
      source = PyToolActionSource.SETTINGS_DETAIL,
    )
  }
}
