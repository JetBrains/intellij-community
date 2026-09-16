// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ty

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.isActiveOn
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [ty](https://github.com/astral-sh/ty) — an extremely fast Python type checker and language server
 * written in Rust by Astral (currently in preview).
 */
@ApiStatus.Internal
class TyPyTool : PyLspTool<TyConfiguration>() {
  override val lspServerName: String = "ty"
  override val icon: Icon = TyUtil.getDefaultTyIcon()
  override val packageName: PyPackageName = PyPackageName.from("ty")

  override fun configuration(project: Project): TyConfiguration = project.service()

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    val manager = LspClientManager.getInstance(project)
    if (isActiveOn(project)) manager.startClientsIfNeeded(TyLspIntegrationProvider::class.java)
    else manager.stopClients(TyLspIntegrationProvider::class.java)
  }

  override fun isSelectedAsTypeEngine(project: Project): Boolean =
    PyTypeEngineProjectSettings.getInstance(project).typeEngine == PyTypeEngineType.TY

  companion object {
    fun getInstance(): TyPyTool = PyTool.EP_NAME.findExtensionOrFail(TyPyTool::class.java)
  }
}
