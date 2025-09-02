// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.jetbrains.python.PythonBinary


/**
 * Vanilla (not conda) has [pythonBinary]
 */
interface VanillaPythonWithLanguageLevel : PythonWithLanguageLevel {
  val pythonBinary: PythonBinary
  override val asExecutablePython: ExecutablePython get() = ExecutablePython.vanillaExecutablePython(pythonBinary)

}