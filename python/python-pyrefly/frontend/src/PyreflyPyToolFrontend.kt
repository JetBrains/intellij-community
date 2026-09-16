// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly.frontend

import com.intellij.python.lsp.core.frontend.LspPyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pyrefly.common.icons.PythonPyreflyCommonIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

internal class PyreflyPyToolFrontend : LspPyToolFrontend {
  override val presentableName: String = "Pyrefly"
  override val description: String get() = PyreflyFrontendBundle.message("pyrefly.tool.description")
  override val toolId: PyToolId = PyToolId("pyrefly")
  override val icon: Icon = IconUtil.resizeSquared(PythonPyreflyCommonIcons.Pyrefly, 16)
}
