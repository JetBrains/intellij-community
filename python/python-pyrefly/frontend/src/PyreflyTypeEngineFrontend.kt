// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly.frontend

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import com.intellij.python.lsp.core.common.PyLspToolConfigurationDto
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.getConfiguration
import com.intellij.python.pytools.common.PyToolSetConfigurationRequest
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.python.typeEngine.frontend.PyTypeEngineFrontend
import com.intellij.ui.components.Badge
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.RowsRange
import com.intellij.ui.dsl.builder.bindSelected

internal class PyreflyTypeEngineFrontend : PyTypeEngineFrontend {
  override val id: PyTypeEngineId = PyTypeEngineId.PYREFLY
  override val presentableName: @NlsSafe String = "Pyrefly"

  override fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange {
    val request = PyToolRequest(project.projectId(), PyToolId(id.packageName))
    fun load(): PyLspToolConfigurationDto = runWithModalProgressBlocking(project, presentableName) {
      PyToolApi.getInstance().getConfiguration<PyLspToolConfigurationDto>(request)
    }

    val initial = load()
    val inlayHints = propertyGraph.property(initial.inlayHints ?: false)
    val documentation = propertyGraph.property(initial.documentation ?: false)
    val result = rowsRange {
      row {
        comment(PyreflyFrontendBundle.message("pyrefly.type.engine.description")).gap(RightGap.SMALL)
        icon(Badge.beta)
      }.layout(RowLayout.INDEPENDENT)

      collapsibleGroup(PyreflyFrontendBundle.message("pyrefly.additional.settings.title"), indent = true) {
        initial.inlayHints?.let {
          row("") {
            checkBox(PyreflyFrontendBundle.message("pyrefly.inlay.hints")).bindSelected(inlayHints)
          }
        }
        initial.documentation?.let {
          row("") {
            checkBox(PyreflyFrontendBundle.message("pyrefly.documentation")).bindSelected(documentation)
          }
        }
      }.expanded = false
    }

    onReset {
      val configuration = load()
      inlayHints.set(configuration.inlayHints ?: false)
      documentation.set(configuration.documentation ?: false)
    }
    onApply {
      runWithModalProgressBlocking(project, presentableName) {
        PyToolApi.getInstance().setConfiguration(
          PyToolSetConfigurationRequest(
            request,
            initial.copy(inlayHints = inlayHints.get(), documentation = documentation.get()),
          )
        )
      }
    }
    return result
  }
}
