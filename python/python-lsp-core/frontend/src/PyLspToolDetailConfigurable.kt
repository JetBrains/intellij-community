// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.frontend

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import com.intellij.python.lsp.core.common.PyLspToolConfigurationDto
import com.intellij.python.pytools.common.PyToolActionSource
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolEventKind
import com.intellij.python.pytools.common.PyToolLogEventRequest
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolSetConfigurationRequest
import com.intellij.python.pytools.common.getConfiguration
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

internal fun createLspToolConfigurable(project: Project, tool: LspPyToolFrontend): UnnamedConfigurable =
  PyLspToolDetailConfigurable(project, tool)

private class PyLspToolDetailConfigurable(
  private val project: Project,
  private val tool: LspPyToolFrontend,
) : BoundConfigurable(tool.presentableName) {
  private val request = PyToolRequest(project.projectId(), tool.toolId)
  private val settings: PyLspToolConfigurationDto by lazy {
    val configuration = runWithModalProgressBlocking(project, PyToolsUiBundle.message("settings.external.tools.apply.progress")) {
      PyToolApi.getInstance().getConfiguration<PyLspToolConfigurationDto>(request)
    }
    configuration.copy()
  }

  override fun createPanel(): DialogPanel = panel {
    PyLspToolFeatureRows.build(this, settings)
    settings.formatting?.let {
      row("") {
        checkBox(requireNotNull(tool.formattingLabel)).bindSelected(settings::formatting.toSafeProperty())
      }
    }
    settings.sortImports?.let {
      row("") {
        checkBox(requireNotNull(tool.sortImportsLabel)).bindSelected(settings::sortImports.toSafeProperty())
      }
    }
  }

  override fun apply() {
    super.apply()
    runWithModalProgressBlocking(project, PyToolsUiBundle.message("settings.external.tools.apply.progress")) {
      val api = PyToolApi.getInstance()
      api.setConfiguration(PyToolSetConfigurationRequest(request, settings))
      api.logEvent(PyToolLogEventRequest(request, PyToolActionSource.SETTINGS_DETAIL, PyToolEventKind.CONFIGURATION_CHANGED))
    }
  }
}
