// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.openapi.project.Project
import com.intellij.python.pytools.ui.PyLspToolDetailConfigurable

internal class PyreflyDetailConfigurable(
  project: Project
) : PyLspToolDetailConfigurable<PyreflyConfiguration>(
  project = project,
  tool = PyreflyPyTool.getInstance()
)
