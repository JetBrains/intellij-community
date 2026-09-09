// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyright.frontend

import com.intellij.python.lsp.core.frontend.LspPyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pyright.common.icons.PythonPyrightCommonIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

internal class PyrightPyToolFrontend : LspPyToolFrontend {
  override val presentableName: String = "Pyright"
  override val description: String get() = PyrightFrontendBundle.message("pyright.tool.description")
  override val toolId: PyToolId = PyToolId("pyright")
  override val icon: Icon = IconUtil.resizeSquared(PythonPyrightCommonIcons.Pyright, 16)
}
