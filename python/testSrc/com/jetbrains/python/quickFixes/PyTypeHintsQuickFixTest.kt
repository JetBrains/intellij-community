// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyTypeHintsInspection
import com.jetbrains.python.psi.LanguageLevel

class PyTypeHintsQuickFixTest : PyQuickFixTestCase() {

  // PY-16853
  fun testParenthesesAndTyping() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with square brackets", LanguageLevel.PYTHON35)
  }

  // PY-16853
  fun testParenthesesAndTypingNoArguments() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with square brackets", LanguageLevel.PYTHON35)
  }

  // PY-16853
  fun testParenthesesAndCustom() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with square brackets", LanguageLevel.PYTHON35)
  }

  // PY-16853
  fun testParenthesesAndCustomNoArguments() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with square brackets", LanguageLevel.PYTHON35)
  }

  // PY-16853
  fun testParenthesesAndTypingTarget() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with square brackets", LanguageLevel.PYTHON35)
  }

  // PY-16853
  fun testParenthesesAndCustomTarget() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with square brackets", LanguageLevel.PYTHON35)
  }
}
