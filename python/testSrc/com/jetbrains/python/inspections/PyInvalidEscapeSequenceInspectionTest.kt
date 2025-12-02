// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyInvalidEscapeSequenceInspectionTest : PyInspectionTestCase() {

  override fun getInspectionClass(): Class<out PyInspection> = PyInvalidEscapeSequenceInspection::class.java

  fun testBasic() = doTest()

  fun testBytes() = doTest()

  fun testFstrings() = doTest()

  fun testEscapeBackslash() {
    doQuickFixTest(PyPsiBundle.message("QFIX.escape.backslash"), "escapeBackslash_before.py", "escapeBackslash_after.py")
  }

  fun testConvertToRawString() {
    doQuickFixTest(PyPsiBundle.message("QFIX.convert.to.raw.string"), "convertToRawString_before.py", "convertToRawString_after.py")
  }

  fun testConvertToRawStringNotAvailable() {
    myFixture.configureByFile(testCaseDirectory + "convertToRawStringNotAvailable_before.py")
    myFixture.enableInspections(getInspectionClass())

    val actions = myFixture.filterAvailableIntentions(PyPsiBundle.message("QFIX.convert.to.raw.string"))
    assertEmpty("Convert to raw string should not be available if string contains valid escapes", actions)

    assertNotNull(myFixture.findSingleIntention(PyPsiBundle.message("QFIX.escape.backslash")))
  }

  private fun doQuickFixTest(hint: String, sourceFile: String, expectedFile: String) {
    myFixture.configureByFile(testCaseDirectory + sourceFile)
    myFixture.enableInspections(getInspectionClass())

    val action = myFixture.findSingleIntention(hint)
    myFixture.launchAction(action)
    myFixture.checkResultByFile(testCaseDirectory + expectedFile)
  }
}
