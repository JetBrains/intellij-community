// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import com.jetbrains.python.PythonLanguage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyCodeStyleSettingsProvider : CodeStyleSettingsCustomDataSynchronizer<PyCodeStyleSettings>() {
  override val language: PythonLanguage get() = PythonLanguage.INSTANCE

  override val customCodeStyleSettingsClass: Class<PyCodeStyleSettings> get() = PyCodeStyleSettings::class.java
}