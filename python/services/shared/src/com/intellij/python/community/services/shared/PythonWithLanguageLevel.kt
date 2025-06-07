// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.Nls

/**
 * Python (vanilla, conda, whatever) with known language level.
 */
interface PythonWithLanguageLevel : Comparable<PythonWithLanguageLevel> {
  val languageLevel: LanguageLevel

  /**
   * Convert python to something that can be executed on [java.util.concurrent.ExecutorService]
   */
  val asExecutablePython: ExecutablePython

  /**
   * Name can be displayed to the end user
   */
  suspend fun getReadableName(): @Nls String

}