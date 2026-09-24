// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.frontend

import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend
import com.intellij.python.pytools.common.FusId
import com.intellij.python.uv.frontend.PyUvFrontendBundle.message
import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import javax.swing.Icon

internal class UvPyToolFrontend : PackageManagerPyToolFrontend {
  override val presentableName: String = "uv"
  override val fusId: FusId = FusId("uv")
  override val description: String get() = message("python.uv.tool.description")
  override val icon: Icon get() = PythonUvCommonIcons.UV
}
