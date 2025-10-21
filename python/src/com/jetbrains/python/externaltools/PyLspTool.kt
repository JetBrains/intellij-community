// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.externaltools

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class PyLspTool(@NlsSafe val presentableName: String, val  packageName: String) {
  BASEDPYRIGHT("basedpyright", "basedpyright"),
  PYREFLY("Pyrefly", "pyrefly"),
  PYRIGHT("Pyright", "pyright"),
  RUFF("Ruff", "ruff"),
  TY("ty", "ty"),
}
