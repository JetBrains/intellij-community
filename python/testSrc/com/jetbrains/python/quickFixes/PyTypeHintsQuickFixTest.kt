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

  // PY-28243
  fun testTypeVarAndTargetName() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with target name")
  }

  // PY-28249
  fun testInstanceCheckOnCallable() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Remove generic parameter(s)")
  }

  // PY-28249
  fun testInstanceCheckOnCustom() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Remove generic parameter(s)")
  }

  // PY-28249
  fun testInstanceCheckOnReference() {
    myFixture.enableInspections(PyTypeHintsInspection::class.java)
    myFixture.configureByFile("${getTestName(true)}.py")
    myFixture.checkHighlighting(true, false, false)

    assertEmpty(myFixture.filterAvailableIntentions("Remove generic parameter(s)"))
  }

  // PY-28249
  fun testInstanceCheckOnOperandReference() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Remove generic parameter(s)")
  }

  // PY-20530
  fun testCallableMoreThanTwoElements() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Surround with square brackets", LanguageLevel.PYTHON37)
  }

  // PY-20530
  fun testCallableTwoElements() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Surround with square brackets", LanguageLevel.PYTHON37)
  }

  // PY-20530
  fun testCallableTwoElementsTupleAsFirst() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with square brackets", LanguageLevel.PYTHON37)
  }

  // PY-20530
  fun testCallableTwoElementsParenthesizedAsFirst() {
    doQuickFixTest(PyTypeHintsInspection::class.java, "Replace with square brackets", LanguageLevel.PYTHON37)
  }
}
