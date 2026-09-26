// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.codeStyle.properties.CodeStyleChoiceList
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor
import com.intellij.psi.codeStyle.CodeStyleSettings

/**
 * Exposes the active Python code style profile as the `ij_python_code_style_profile` .editorconfig
 * property so the "default" / "classic" choice (PY-85946) round-trips through .editorconfig.
 */
class PyCodeStylePropertyAccessor(private val pyCodeStyle: PyCodeStyleSettings) :
  CodeStylePropertyAccessor<String>(),
  CodeStyleChoiceList {
  override fun set(extVal: String): Boolean = applyPyCodeStyle(extVal, pyCodeStyle.container)
  override fun get(): String? = pyCodeStyle.container.pyCodeStyleProfile()
  override fun parseString(string: String): String = string
  override fun valueToString(value: String): String = value
  override fun getChoices(): List<String> = listOf(PyDefaultStyleGuide.CODE_STYLE_ID, PyClassicStyleGuide.CODE_STYLE_ID)
  override fun getPropertyName(): String = "code_style_profile"
}

private fun applyPyCodeStyle(codeStyleId: String?, settings: CodeStyleSettings): Boolean {
  when (codeStyleId) {
    PyDefaultStyleGuide.CODE_STYLE_ID -> PyDefaultStyleGuide.apply(settings)
    PyClassicStyleGuide.CODE_STYLE_ID -> PyClassicStyleGuide.apply(settings)
    else -> return false
  }
  return true
}
