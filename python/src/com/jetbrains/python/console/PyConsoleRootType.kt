// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.execution.console.ConsoleRootType
import com.intellij.ide.scratch.RootType

class PyConsoleRootType internal constructor() : ConsoleRootType("py", "Python Consoles") {
  companion object {

    val instance: PyConsoleRootType
      get() = RootType.findByClass(PyConsoleRootType::class.java)
  }
}
