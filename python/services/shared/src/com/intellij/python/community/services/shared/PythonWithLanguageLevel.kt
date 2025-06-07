// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.jetbrains.python.psi.LanguageLevel

/**
 * Python (vanilla, conda, whatever) with known language level.
 */
interface PythonWithLanguageLevel : Comparable<PythonWithLanguageLevel>, PythonWithName {
  val languageLevel: LanguageLevel

  /**
   * Convert python to something that can be executed on [java.util.concurrent.ExecutorService]
   */
  val asExecutablePython: ExecutablePython


  // Backward: first python is the highest
  override fun compareTo(other: PythonWithLanguageLevel): Int = languageLevel.compareTo(other.languageLevel) * -1

}