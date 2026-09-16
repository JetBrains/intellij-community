// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.frontend

import com.intellij.python.lsp.core.frontend.LspPyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.ruff.common.icons.PythonRuffCommonIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

internal class RuffPyToolFrontend : LspPyToolFrontend {
  override val presentableName: String = "Ruff"
  override val description: String get() = RuffFrontendBundle.message("ruff.tool.description")
  override val toolId: PyToolId = PyToolId("ruff")
  override val icon: Icon = IconUtil.resizeSquared(PythonRuffCommonIcons.Ruff, 16)
  override val formattingLabel: String get() = RuffFrontendBundle.message("checkbox.formatting")
  override val sortImportsLabel: String get() = RuffFrontendBundle.message("checkbox.import.optimizer")
}
