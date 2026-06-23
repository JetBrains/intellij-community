// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ty

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.lsp.PyLspTool
import com.intellij.python.pytools.ui.PyToolDetailConfigurableProvider
import com.intellij.python.pytools.ui.pyLspToolFeaturesSummary
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class TyPyTool : PyLspTool<TyConfiguration>(), PyToolDetailConfigurableProvider {
  override val presentableName: String = "ty"
  override val description: String get() = TyBundle.message("ty.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("ty")

  override fun configuration(project: Project): TyConfiguration = project.service<TyConfiguration>()

  override val icon: Icon = TyUtil.getDefaultTyIcon()

  override fun createConfigurable(project: Project): TyConfigurable = TyConfigurable(project)

  override fun summaryFor(project: Project): String = pyLspToolFeaturesSummary(configuration(project))

  override fun onEnabledChanged(project: Project, enabled: Boolean) {
    val manager = LspClientManager.getInstance(project)
    if (enabled) manager.startClientsIfNeeded(TyLspIntegrationProvider::class.java)
    else manager.stopClients(TyLspIntegrationProvider::class.java)
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): TyPyTool = PyTool.EP_NAME.findExtensionOrFail(TyPyTool::class.java)
  }
}
