// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.frontend

import com.intellij.python.pixi.common.icons.PythonPixiCommonIcons
import com.intellij.python.pytools.common.FusId
import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend
import javax.swing.Icon

internal class PixiPyToolFrontend : PackageManagerPyToolFrontend {
  override val presentableName: String = "Pixi"
  override val fusId: FusId = FusId("pixi")
  override val description: String get() = PyPixiFrontendBundle.message("python.pixi.tool.description")
  override val icon: Icon get() = PythonPixiCommonIcons.Pixi
}
