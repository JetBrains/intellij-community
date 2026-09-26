// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyright

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.pytools.backend.PyTool
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [Pyright](https://microsoft.github.io/pyright/) — a fast static type checker for Python from
 * Microsoft, providing type checking and language-server features such as completions and hovers.
 */
@ApiStatus.Internal
class PyrightPyTool : PyLspTool<PyrightConfiguration>() {
  override val lspServerName: String = "Pyright"
  override val icon: Icon = PyrightUtil.getDefaultPyrightIcon()
  override val packageName: PyPackageName = PyPackageName.from("pyright")

  override fun configuration(project: Project): PyrightConfiguration = project.service()

  override fun onEnabledChanged(project: Project, enabled: Boolean): Unit = restartOrStopPyrightProvider(project)

  companion object {
    fun getInstance(): PyrightPyTool = PyTool.EP_NAME.findExtensionOrFail(PyrightPyTool::class.java)
  }
}
