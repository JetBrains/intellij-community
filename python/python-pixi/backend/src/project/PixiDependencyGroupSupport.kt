// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.project

import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.PySdkDependencyGroupSupport
import com.intellij.python.sdk.backend.PythonInterpreter

internal object PixiDependencyGroupSupport : PySdkDependencyGroupSupport {
  override suspend fun formatDependencyGroupArgs(
    interpreter: PythonInterpreter,
    group: PyDependencyGroup,
    pyProjectToml: PyProjectToml?,
  ): List<String> = listOf("--feature=${if (group.name == "main") "default" else group.name}")
}
