// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.typeEngine

import org.jetbrains.annotations.Nls

enum class PyTypeEngineType(val displayName: @Nls String, val packageName: String) {
  PYCHARM("Built-in", ""),
  PYREFLY("Pyrefly", "pyrefly"),
  TY("ty", "ty");
}
