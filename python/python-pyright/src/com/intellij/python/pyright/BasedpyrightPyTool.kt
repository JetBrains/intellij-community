// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyright

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.lsp.PyLspTool
import com.intellij.python.pytools.ui.PyToolDetailConfigurableProvider
import com.intellij.python.pytools.ui.pyLspToolFeaturesSummary
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class BasedpyrightPyTool : PyLspTool<BasedpyrightConfiguration>(), PyToolDetailConfigurableProvider {
  override val presentableName: String = "Basedpyright"
  override val description: String get() = PyrightBundle.message("basedpyright.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("basedpyright")

  override fun configuration(project: Project): BasedpyrightConfiguration = project.service<BasedpyrightConfiguration>()

  override val icon: Icon = PyrightUtil.getDefaultBasedPyrightIcon()

  override fun createConfigurable(project: Project): BasedpyrightConfigurable = BasedpyrightConfigurable(project)

  override fun summaryFor(project: Project): String = pyLspToolFeaturesSummary(configuration(project))

  override fun onEnabledChanged(project: Project, enabled: Boolean): Unit = restartOrStopPyrightProvider(project)

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): BasedpyrightPyTool = PyTool.EP_NAME.findExtensionOrFail(BasedpyrightPyTool::class.java)
  }
}
