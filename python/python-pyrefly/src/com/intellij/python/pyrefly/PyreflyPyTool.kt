// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.openapi.components.service
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.statistics.PyToolFusSnapshot
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyreflyPyTool : PyTool {
  override val presentableName: String = "Pyrefly"
  override val description: String get() = PyreflyBundle.message("pyrefly.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("pyrefly")

  override fun migrateLegacyState(project: Project): PyToolsState.ToolEntry = project.service<PyreflyConfiguration>().migrateToPyToolState()

  override val detailConfigurable: (Project) -> UnnamedConfigurable = ::PyreflyDetailConfigurable

  override fun summaryFor(project: Project): String {
    val cfg = project.service<PyreflyConfiguration>()
    return buildList {
      if (cfg.inspections) add(PyToolsUiBundle.message("checkbox.inspections"))
      if (cfg.completions == true) add(PyToolsUiBundle.message("checkbox.completions"))
      if (cfg.inlayHints == true) add(PyToolsUiBundle.message("checkbox.inlay.hints"))
      if (cfg.documentation == true) add(PyToolsUiBundle.message("checkbox.documentation"))
    }.joinToString(", ")
  }

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    val manager = LspClientManager.getInstance(project)
    if (enabled) manager.stopAndRestartClientsIfNeeded<PyreflyLspIntegrationProvider>()
    else manager.stopClients<PyreflyLspIntegrationProvider>()
  }

  override fun configurationFusSnapshot(project: Project): PyToolFusSnapshot {
    val cfg = project.service<PyreflyConfiguration>()
    return super.configurationFusSnapshot(project).copy(
      inspections = cfg.inspections,
      completions = cfg.completions,
      inlayHints = cfg.inlayHints,
      documentation = cfg.documentation,
    )
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PyreflyPyTool = PyTool.EP_NAME.findExtensionOrFail(PyreflyPyTool::class.java)
  }
}
