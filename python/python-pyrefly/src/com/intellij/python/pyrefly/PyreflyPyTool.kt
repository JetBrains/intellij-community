// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.isActiveOn
import com.intellij.python.pyrefly.lsp.BundledPyreflyLspIntegrationProvider
import com.intellij.python.pyrefly.lsp.PyreflyLspIntegrationProvider
import com.intellij.python.pytools.backend.PyTool
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon

/**
 * [Pyrefly](https://pyrefly.org/) — a fast Python type checker written in Rust by Meta, providing type
 * checking and IDE features through a language server.
 *
 * Pyrefly runs two servers, and each one has its own provider:
 * - The External Tools Pyrefly runs the `pyrefly` of the environment, see [PyreflyLspIntegrationProvider].
 *   The External Tools toggle controls it, see [isActiveOn].
 * - The bundled Pyrefly runs the binary of the plugin, see [BundledPyreflyLspIntegrationProvider]. Nothing
 *   starts it yet, see [runsBundledPyrefly].
 */
@ApiStatus.Internal
class PyreflyPyTool : PyLspTool<PyreflyConfiguration>() {
  override val lspServerName: String = "Pyrefly"
  override val icon: Icon = PyreflyUtil.getDefaultPyreflyIcon()
  override val packageName: PyPackageName = PyPackageName.from("pyrefly")

  override fun configuration(project: Project): PyreflyConfiguration = project.service()

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    val manager = LspClientManager.getInstance(project)
    if (isActiveOn(project)) manager.stopAndRestartClientsIfNeeded<PyreflyLspIntegrationProvider>()
    else manager.stopClients<PyreflyLspIntegrationProvider>()
  }

  /** Whether the bundled Pyrefly runs for [project]. Nothing starts it yet, so only a test turns it on, see [runBundledPyreflyInTests]. */
  fun runsBundledPyrefly(project: Project): Boolean = project.getUserData(RUNS_BUNDLED_PYREFLY_KEY) == true

  companion object {
    private val RUNS_BUNDLED_PYREFLY_KEY: Key<Boolean> = Key.create("PyreflyPyTool.runsBundledPyrefly")

    fun getInstance(): PyreflyPyTool = PyTool.EP_NAME.findExtensionOrFail(PyreflyPyTool::class.java)

    /** Runs the bundled Pyrefly for [project] until [parentDisposable] is disposed. */
    @TestOnly
    fun runBundledPyreflyInTests(project: Project, parentDisposable: Disposable) {
      project.putUserData(RUNS_BUNDLED_PYREFLY_KEY, true)
      Disposer.register(parentDisposable) { project.putUserData(RUNS_BUNDLED_PYREFLY_KEY, null) }
    }
  }
}
