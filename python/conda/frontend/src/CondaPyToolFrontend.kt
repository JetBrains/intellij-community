// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.conda.frontend

import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.community.impl.conda.common.icons.PythonCommunityImplCondaCommonIcons
import javax.swing.Icon

internal class CondaPyToolFrontend : PackageManagerPyToolFrontend {
  override val presentableName: String = "Conda"
  override val toolId: PyToolId = PyToolId("conda")
  override val description: String get() = PyCondaFrontendBundle.message("python.conda.tool.description")
  override val icon: Icon get() = PythonCommunityImplCondaCommonIcons.Anaconda
}
