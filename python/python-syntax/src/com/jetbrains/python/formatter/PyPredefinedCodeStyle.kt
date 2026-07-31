// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.lang.Language
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.PredefinedCodeStyle
import com.jetbrains.python.PythonLanguage

sealed class PyPredefinedCodeStyle(name: @NlsContexts.ListItem String) : PredefinedCodeStyle(name, PythonLanguage.getInstance()) {
  abstract val codeStyleId: String

  // The whole PY-85946 feature is inert while the master switch is off, so the profiles must not be
  // offered in the Code Style settings either (otherwise the historical behavior isn't byte-identical).
  override fun isApplicableToLanguage(language: Language): Boolean =
    isPyNewFormatterDefaultsFeatureEnabled() && super.isApplicableToLanguage(language)
}

class PyDefaultPredefinedCodeStyle : PyPredefinedCodeStyle(PyDefaultStyleGuide.CODE_STYLE_TITLE) {
  override val codeStyleId: String = PyDefaultStyleGuide.CODE_STYLE_ID

  override fun apply(settings: CodeStyleSettings) {
    PyDefaultStyleGuide.apply(settings)
  }
}

class PyClassicPredefinedCodeStyle : PyPredefinedCodeStyle(PyClassicStyleGuide.CODE_STYLE_TITLE) {
  override val codeStyleId: String = PyClassicStyleGuide.CODE_STYLE_ID

  override fun apply(settings: CodeStyleSettings) {
    PyClassicStyleGuide.apply(settings)
  }
}
