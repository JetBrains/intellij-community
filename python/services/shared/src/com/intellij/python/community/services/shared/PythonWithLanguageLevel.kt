// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.intellij.python.community.execService.python.advancedApi.ExecutablePython

/**
 * Python (vanilla, conda, whatever) with known language level.
 */
interface PythonWithLanguageLevel : PythonWithName, LanguageLevelHolder {

  /**
   * Convert python to something that can be executed on [java.util.concurrent.ExecutorService]
   */
  val asExecutablePython: ExecutablePython

}