// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python.advancedApi

import com.jetbrains.python.PythonBinary
import java.nio.file.Path

/**
 * Something that can execute python code (vanilla cpython, conda).
 * `[binary] [args]` <python-args-go-here> (i.e `-m foo.py`).
 *   For [VanillaExecutablePython] it is `python` without arguments, but for conda it might be `conda run` etc
 */
interface ExecutablePython {
  val binary: Path
  val args: List<String>

  companion object {
    class VanillaExecutablePython(override val binary: PythonBinary) : ExecutablePython {
      override val args: List<String> = emptyList()
    }
  }
}
