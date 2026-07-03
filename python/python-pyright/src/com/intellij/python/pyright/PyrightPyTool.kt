// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyright

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.lsp.PyLspTool
import com.intellij.python.pytools.configuration.ConfigurablePyTool
import com.intellij.python.pytools.ui.pyLspToolFeaturesSummary
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [Pyright](https://microsoft.github.io/pyright/) — a fast static type checker for Python from
 * Microsoft, providing type checking and language-server features such as completions and hovers.
 */
@ApiStatus.Internal
class PyrightPyTool : PyLspTool<PyrightConfiguration>(), ConfigurablePyTool {
  override val presentableName: String = "Pyright"
  override val description: String get() = PyrightBundle.message("pyright.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("pyright")

  override fun configuration(project: Project): PyrightConfiguration = project.service<PyrightConfiguration>()

  override val icon: Icon = PyrightUtil.getDefaultPyrightIcon()

  override fun createConfigurable(project: Project): PyrightConfigurable = PyrightConfigurable(project)

  override fun summaryFor(project: Project): String = pyLspToolFeaturesSummary(configuration(project))

  override fun onEnabledChanged(project: Project, enabled: Boolean): Unit = restartOrStopPyrightProvider(project)

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PyrightPyTool = PyTool.EP_NAME.findExtensionOrFail(PyrightPyTool::class.java)
  }
}
