// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.venv.frontend

import com.intellij.python.pytools.frontend.PyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.venv.common.icons.PythonVenvCommonIcons
import com.intellij.python.venv.frontend.PyVenvFrontendBundle.message
import javax.swing.Icon

internal class PipPyToolFrontend : PyToolFrontend {
  override val presentableName: String = "pip"
  override val toolId: PyToolId = PyToolId("pip")
  override val description: String get() = message("py.venv.pip.tool.description")
  override val icon: Icon get() = PythonVenvCommonIcons.VirtualEnv
}
