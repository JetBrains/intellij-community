// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python.advancedApi

import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.execService.python.PythonBinaryOnEelOrTarget
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython.Companion.vanillaExecutablePython
import com.jetbrains.python.PythonBinary

/**
 * Something that can execute python code (vanilla cpython, conda).
 * `[binary] [args]` <python-args-go-here> (i.e `-m foo.py`).
 * [env] are added to the environment
 *   For [vanillaExecutablePython] it is `python` without arguments, but for conda it might be `conda run` etc
 */
data class ExecutablePython(
  val binary: BinaryToExec,
  val args: List<String>,
  val env: Map<String, String>,
) {

  companion object {
    /**
     * Plain python that doesn't have args nor envs
     */
    fun vanillaExecutablePython(binary: PythonBinaryOnEelOrTarget): ExecutablePython =
      ExecutablePython(binary, emptyList(), emptyMap())

    fun vanillaExecutablePython(binary: PythonBinary): ExecutablePython = vanillaExecutablePython(binary.asBinToExec())
  }

}
