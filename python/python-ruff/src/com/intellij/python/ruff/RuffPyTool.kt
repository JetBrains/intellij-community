// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.common.PyLspToolConfigurationDto
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.isEnabledOn
import com.intellij.python.pytools.backend.statistics.PyToolFusSnapshot
import com.intellij.python.ruff.server.RuffLspIntegrationProvider
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [Ruff](https://docs.astral.sh/ruff/) — an extremely fast Python linter and code formatter written in
 * Rust by Astral. It combines linting, import sorting, and formatting in a single tool, aiming to
 * replace Flake8, isort, pyupgrade, and Black.
 */
@ApiStatus.Internal
class RuffPyTool : PyLspTool<RuffConfiguration>() {
  override val lspServerName: String = "Ruff"
  override val icon: Icon = RuffUtil.getDefaultRuffIcon()
  override val packageName: PyPackageName = PyPackageName.from("ruff")

  override fun configuration(project: Project): RuffConfiguration = project.service()

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    // Enabling can follow an install that the retry budget already gave up on. Disabling must not
    // invalidate: a read arriving after it would start a query for a tool the user just turned off.
    if (enabled) project.serviceIfCreated<RuffService>()?.invalidate()
    val manager = LspClientManager.getInstance(project)
    if (enabled) manager.startClientsIfNeeded(RuffLspIntegrationProvider::class.java)
    else manager.stopClients(RuffLspIntegrationProvider::class.java)
  }

  override fun configurationState(project: Project): PyLspToolConfigurationDto {
    val configuration = configuration(project)
    return super.configurationState(project).copy(
      formatting = configuration.formatting,
      sortImports = configuration.sortImports,
    )
  }

  override fun applyConfigurationState(project: Project, state: PyLspToolConfigurationDto) {
    super.applyConfigurationState(project, state)
    val configuration = configuration(project)
    state.formatting?.let { configuration.formatting = it }
    state.sortImports?.let { configuration.sortImports = it }
  }

  override fun onExecutableChanged(project: Project) {
    // A running server holds the Ruff as it was resolved when it started. A restart makes the servers
    // current, and each one reports its version. That report retires the catalogue when the version
    // changed, so a burst of restarts starts no query. With no server, nothing reports, so the
    // catalogue is retired here. Nothing runs while the tool is off.
    if (!isEnabledOn(project)) return
    val manager = LspClientManager.getInstance(project)
    if (manager.getClients(RuffLspIntegrationProvider::class.java).isEmpty()) {
      project.serviceIfCreated<RuffService>()?.invalidate()
    }
    manager.stopAndRestartClientsIfNeeded(RuffLspIntegrationProvider::class.java)
  }

  override fun configurationFusSnapshot(project: Project): PyToolFusSnapshot {
    val configuration = configuration(project)
    return super.configurationFusSnapshot(project).copy(
      formatting = configuration.formatting,
      sortImports = configuration.sortImports,
    )
  }

  companion object {
    fun getInstance(): RuffPyTool = PyTool.EP_NAME.findExtensionOrFail(RuffPyTool::class.java)
  }
}
