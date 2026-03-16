// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyPatternInspection

@TestDataPath("\$CONTENT_ROOT/../testData/quickFixes/PySimplifyAsPatternFixTest")
class PySimplifyAsPatternFixTest : PyQuickFixTestCase() {
  fun testSimplifyListAsPattern() {
    doQuickFixTest(PyPsiBundle.message("QFIX.simplify.as.pattern"))
  }

  fun testSimplifyIntAsPattern() {
    doQuickFixTest(PyPsiBundle.message("QFIX.simplify.as.pattern"))
  }

  // Overriden to enable weak warnings
  override fun doQuickFixTest(hint: String) {
    val testFileName = getTestName(true)
    myFixture.enableInspections(PyPatternInspection::class.java)
    myFixture.configureByFile("$testFileName.py")
    myFixture.checkHighlighting(false, false, true)
    val intentionAction = myFixture.findSingleIntention(hint)
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile(testFileName + "_after.py", true)
  }
}
