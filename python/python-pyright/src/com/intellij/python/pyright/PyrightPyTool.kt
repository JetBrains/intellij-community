// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyright

import com.intellij.openapi.components.service
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.python.pytools.InstallInfo
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.lsp.executablePath
import com.intellij.python.pytools.statistics.PyToolFusSnapshot
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class PyrightPyTool : PyTool {
  override val presentableName: String = "Pyright"
  override val description: String get() = PyrightBundle.message("pyright.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("pyright")
  override val aliases: List<PyPackageName> = listOf(packageName, PyPackageName.from("basedpyright"))

  override val installInfo: InstallInfo = InstallInfo(
    packageName = PyPackageName.from("basedpyright"),
    installHelp = PyrightBundle.message("basedpyright.help"),
  )

  override fun legacyEnabled(project: Project): Boolean =
    project.service<PyrightConfiguration>().enabled

  override fun legacyDiscoveryMode(project: Project): ExecutableDiscoveryMode =
    project.service<PyrightConfiguration>().executableDiscoveryMode

  override fun legacyCustomPath(project: Project): Path? =
    project.service<PyrightConfiguration>().executablePath

  override val detailConfigurable: (Project) -> UnnamedConfigurable = ::PyrightConfigurable

  override fun summaryFor(project: Project): String {
    val cfg = project.service<PyrightConfiguration>()
    return buildList {
      if (cfg.inspections) add(PyToolsUiBundle.message("checkbox.inspections"))
      if (cfg.completions == true) add(PyToolsUiBundle.message("checkbox.completions"))
      if (cfg.inlayHints == true) add(PyrightBundle.message("checkbox.inlay.hints.basedpyright.only"))
      if (cfg.documentation == true) add(PyToolsUiBundle.message("checkbox.documentation"))
    }.joinToString(", ")
  }

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    val manager = LspClientManager.getInstance(project)
    if (enabled) manager.startClientsIfNeeded(PyrightLspIntegrationProvider::class.java)
    else manager.stopClients(PyrightLspIntegrationProvider::class.java)
  }

  override fun configurationFusSnapshot(project: Project): PyToolFusSnapshot {
    val cfg = project.service<PyrightConfiguration>()
    return super.configurationFusSnapshot(project).copy(
      inspections = cfg.inspections,
      completions = cfg.completions,
      inlayHints = cfg.inlayHints,
      documentation = cfg.documentation,
    )
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PyrightPyTool = PyTool.EP_NAME.findExtensionOrFail(PyrightPyTool::class.java)
  }
}
