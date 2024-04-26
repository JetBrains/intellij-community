// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyCompatibilityInspection
import com.jetbrains.python.psi.LanguageLevel

class PyReplaceStarByUnpackQuickFixTest: PyQuickFixTestCase() {
  // PY-53105
  fun testTypeVarTupleAfterTypeVar() {
    doTest(LanguageLevel.PYTHON310)
  }

  // PY-53105
  fun testTypeVarTupleOnly() {
    doTest(LanguageLevel.PYTHON310)
  }

  fun doTest(languageLevel: LanguageLevel) {
    runWithLanguageLevel(languageLevel) {
      val testFileName = getTestName(true)
      myFixture.enableInspections(PyCompatibilityInspection::class.java)
      myFixture.configureByFile("$testFileName.py")
      myFixture.checkHighlighting(false, false, false)
      val intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.replace.star.by.unpack"))
      assertNotNull(intentionAction)
      myFixture.launchAction(intentionAction!!)
      myFixture.checkResultByFile(testFileName + "_after.py", true)
    }
  }
}