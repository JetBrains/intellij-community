// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ty.frontend

import com.intellij.python.lsp.core.frontend.LspPyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.ty.common.icons.PythonTyCommonIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

internal class TyPyToolFrontend : LspPyToolFrontend {
  override val presentableName: String = "ty"
  override val description: String get() = TyFrontendBundle.message("ty.tool.description")
  override val toolId: PyToolId = PyToolId("ty")
  override val icon: Icon = IconUtil.downscaleIconToSize(PythonTyCommonIcons.TY, 16, 16)
}
