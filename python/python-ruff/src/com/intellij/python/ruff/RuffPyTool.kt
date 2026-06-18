// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.openapi.components.service
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.statistics.PyToolFusSnapshot
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.ruff.server.RuffLspIntegrationProvider
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RuffPyTool : PyTool {
  override val presentableName: String = "Ruff"
  override val description: String get() = RuffBundle.message("ruff.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("ruff")

  override fun migrateLegacyState(project: Project): PyToolsState.ToolEntry = project.service<RuffConfiguration>().migrateToPyToolState()

  override val detailConfigurable: (Project) -> UnnamedConfigurable = ::RuffConfigurable

  override fun summaryFor(project: Project): String {
    val cfg = project.service<RuffConfiguration>()
    return buildList {
      if (cfg.inspections) add(PyToolsUiBundle.message("checkbox.inspections"))
      if (cfg.formatting) add(RuffBundle.message("checkbox.formatting"))
      if (cfg.sortImports) add(RuffBundle.message("checkbox.import.optimizer"))
      if (cfg.completions == true) add(PyToolsUiBundle.message("checkbox.completions"))
      if (cfg.inlayHints == true) add(PyToolsUiBundle.message("checkbox.inlay.hints"))
      if (cfg.documentation == true) add(PyToolsUiBundle.message("checkbox.documentation"))
    }.joinToString(", ")
  }

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    val manager = LspClientManager.getInstance(project)
    if (enabled) manager.startClientsIfNeeded(RuffLspIntegrationProvider::class.java)
    else manager.stopClients(RuffLspIntegrationProvider::class.java)
  }

  override fun configurationFusSnapshot(project: Project): PyToolFusSnapshot {
    val cfg = project.service<RuffConfiguration>()
    return super.configurationFusSnapshot(project).copy(
      inspections = cfg.inspections,
      completions = cfg.completions,
      inlayHints = cfg.inlayHints,
      documentation = cfg.documentation,
      formatting = cfg.formatting,
      sortImports = cfg.sortImports,
    )
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): RuffPyTool = PyTool.EP_NAME.findExtensionOrFail(RuffPyTool::class.java)
  }
}
