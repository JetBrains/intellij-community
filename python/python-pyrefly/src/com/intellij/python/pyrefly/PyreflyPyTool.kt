// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.openapi.components.service
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.isActiveOn
import com.intellij.python.pytools.lsp.PyLspTool
import com.intellij.python.pytools.configuration.ConfigurablePyTool
import com.intellij.python.pytools.ui.pyLspToolFeaturesSummary
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [Pyrefly](https://pyrefly.org/) — a fast Python type checker written in Rust by Meta, providing type
 * checking and IDE features through a language server.
 */
@ApiStatus.Internal
class PyreflyPyTool : PyLspTool<PyreflyConfiguration>(), ConfigurablePyTool {
  override val presentableName: String = "Pyrefly"
  override val description: String get() = PyreflyBundle.message("pyrefly.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("pyrefly")

  override fun configuration(project: Project): PyreflyConfiguration = project.service<PyreflyConfiguration>()

  override val icon: Icon = PyreflyUtil.getDefaultPyreflyIcon()

  override fun createConfigurable(project: Project): UnnamedConfigurable = PyreflyDetailConfigurable(project)

  override fun summaryFor(project: Project): String = pyLspToolFeaturesSummary(configuration(project))

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    // Drive the shared LSP server off `isActiveOn` rather than the raw flag: when Pyrefly is the
    // selected type engine the server must keep running even though the tool flag is off.
    val manager = LspClientManager.getInstance(project)
    if (isActiveOn(project)) manager.stopAndRestartClientsIfNeeded<PyreflyLspIntegrationProvider>()
    else manager.stopClients<PyreflyLspIntegrationProvider>()
  }

  override fun isSelectedAsTypeEngine(project: Project): Boolean =
    Registry.`is`("pyrefly.type.engine") ||
    PyTypeEngineProjectSettings.getInstance(project).typeEngine == PyTypeEngineType.PYREFLY

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PyreflyPyTool = PyTool.EP_NAME.findExtensionOrFail(PyreflyPyTool::class.java)
  }
}
