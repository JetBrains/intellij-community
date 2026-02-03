// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyInconsistentReturnsInspection

@TestDataPath("\$CONTENT_ROOT/../testData/quickFixes/PyMakeReturnExplicitFixTest")
class PyMakeReturnExplicitFixTest : PyQuickFixTestCase() {
  fun testAddExplicitReturn() {
    doQuickFixTest(PyPsiBundle.message("QFIX.add.explicit.return.none"))
  }

  fun testReplaceWithReturnNone() {
    doQuickFixTest(PyPsiBundle.message("QFIX.replace.with.return.none"))
  }


  // Copied from PyQuickFixTestCase to enable weak warnings
  override fun doQuickFixTest(hint: String) {
    val testFileName = getTestName(true)
    myFixture.enableInspections(PyInconsistentReturnsInspection::class.java)
    myFixture.configureByFile("$testFileName.py")
    myFixture.checkHighlighting(false, false, true)
    val intentionAction = myFixture.findSingleIntention(hint)
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile(testFileName + "_after.py", true)
  }
}