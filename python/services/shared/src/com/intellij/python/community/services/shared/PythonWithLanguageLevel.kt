// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.jetbrains.python.PythonBinary
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.Nls

interface PythonWithLanguageLevel : Comparable<PythonWithLanguageLevel> {
  val pythonBinary: PythonBinary
  val languageLevel: LanguageLevel

  /**
   * Name can be displayed to the end user
   */
  suspend fun getReadableName(): @Nls String
}