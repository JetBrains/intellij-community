// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tasks

import com.jetbrains.python.psi.LanguageLevel

open class PythonDebuggerLanguageLevelTask(val predicate: (LanguageLevel) -> Boolean, relativeTestDataPath: String?, scriptName: String) : PyDebuggerTask(relativeTestDataPath, scriptName) {
  override fun isLanguageLevelSupported(level: LanguageLevel): Boolean {
    return predicate(level)
  }
}