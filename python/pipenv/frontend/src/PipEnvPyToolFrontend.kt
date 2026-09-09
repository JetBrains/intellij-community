// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.pipenv.frontend

import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.community.impl.pipenv.common.icons.PythonCommunityImplPipenvCommonIcons
import com.intellij.python.community.impl.pipenv.frontend.PyPipenvFrontendBundle.message
import javax.swing.Icon

internal class PipEnvPyToolFrontend : PackageManagerPyToolFrontend {
  override val presentableName: String = "Pipenv"
  override val toolId: PyToolId = PyToolId("pipenv")
  override val description: String get() = message("python.pipenv.tool.description")
  override val icon: Icon get() = PythonCommunityImplPipenvCommonIcons.Pipenv
}
