// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.frontend

import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.hatch.common.icons.PythonHatchCommonIcons
import com.intellij.python.hatch.frontend.PyHatchFrontendBundle.message
import javax.swing.Icon

internal class HatchPyToolFrontend : PackageManagerPyToolFrontend {
  override val presentableName: String = "Hatch"
  override val toolId: PyToolId = PyToolId("hatch")
  override val description: String get() = message("python.hatch.tool.description")
  override val icon: Icon get() = PythonHatchCommonIcons.Logo
}
