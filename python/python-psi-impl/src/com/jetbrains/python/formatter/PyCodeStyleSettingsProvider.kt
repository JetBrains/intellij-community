package com.jetbrains.python.formatter

import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import com.jetbrains.python.PythonLanguage

class PyCodeStyleSettingsProvider : CodeStyleSettingsCustomDataSynchronizer<PyCodeStyleSettings>() {
  override val language: PythonLanguage get() = PythonLanguage.INSTANCE

  override val customCodeStyleSettingsClass: Class<PyCodeStyleSettings> get() = PyCodeStyleSettings::class.java
}