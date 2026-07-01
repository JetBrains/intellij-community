package com.intellij.python.pyrefly

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.lsp.core.PyLspCoreBundle
import com.intellij.python.pytools.ui.toSafeProperty
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pyrefly.typeEngine.PyreflyLspTypeEngineProvider
import com.intellij.python.pytools.isEnabledOn
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.RowsRange
import com.intellij.ui.dsl.builder.bindSelected

internal class PyreflyPyTypeEngineProvider : PyTypeEngineProvider {
  override val pyTypeEngineType: PyTypeEngineType
    get() = PyTypeEngineType.PYREFLY

  override fun isSupported(project: Project): Boolean {
    return PyreflyLspTypeEngineProvider.Util.isAvailable(project)
  }

  override fun updateLspServers(project: Project) {
    val lspServerManager = LspClientManager.getInstance(project)
    if (shouldBeEnabled(project)) {
      lspServerManager.stopAndRestartClientsIfNeeded<PyreflyLspIntegrationProvider>()
    }
    else {
      lspServerManager.stopClients<PyreflyLspIntegrationProvider>()
    }
  }

  private fun shouldBeEnabled(project: Project): Boolean {
    return isSupported(project) && PyreflyPyTool.getInstance().isEnabledOn(project)
  }

  override fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange {
    val pyreflySettings = project.service<PyreflyConfiguration>()

    return rowsRange {
      row {
        comment(PyLspCoreBundle.message("pyrefly.description")).gap(RightGap.SMALL)
        icon(AllIcons.General.Beta)
      }.layout(RowLayout.INDEPENDENT)

      collapsibleGroup(PyreflyBundle.message("pyrefly.additional.settings.title"), indent = true) {
        if (pyreflySettings.inlayHints != null) {
          row("") {
            checkBox(PyToolsUiBundle.message("checkbox.inlay.hints"))
              .bindSelected(pyreflySettings::inlayHints.toSafeProperty())
          }
        }
        if (pyreflySettings.documentation != null) {
          row("") {
            checkBox(PyToolsUiBundle.message("checkbox.documentation"))
              .bindSelected(pyreflySettings::documentation.toSafeProperty())
          }
        }
      }.expanded = false
    }
  }
}
