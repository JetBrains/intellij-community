// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyright

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.pytools.backend.PyTool
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [Basedpyright](https://docs.basedpyright.com/) — an open-source fork of Pyright that adds features
 * such as stricter type inference and language-server capabilities otherwise exclusive to Pylance.
 */
@ApiStatus.Internal
class BasedpyrightPyTool : PyLspTool<BasedpyrightConfiguration>() {
  override val lspServerName: String = "Basedpyright"
  override val icon: Icon = PyrightUtil.getDefaultBasedPyrightIcon()
  override val packageName: PyPackageName = PyPackageName.from("basedpyright")

  override fun configuration(project: Project): BasedpyrightConfiguration = project.service()

  override fun onEnabledChanged(project: Project, enabled: Boolean): Unit = restartOrStopPyrightProvider(project)

  companion object {
    fun getInstance(): BasedpyrightPyTool = PyTool.EP_NAME.findExtensionOrFail(BasedpyrightPyTool::class.java)
  }
}
