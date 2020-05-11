// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.LanguageLevel

/**
 * @author Daniel Schmidt
 */
class PyPrefixFStringIntentionTest: PyIntentionTestCase() {

  fun testOlderThanPython36() = doTestWithPythonOlderThan36()

  fun testByteString() = doNegativeTest()
  fun testCaretBeforeString() = doNegativeTest()
  fun testDocstring() = doNegativeTest()
  fun testEscapedOpenCurlyBraces() = doNegativeTest()
  fun testFString() = doNegativeTest()
  fun testNotTerminated() = doNegativeTest()
  fun testNoString() = doNegativeTest()
  fun testUnicodeString() = doNegativeTest()
  fun testWithoutOpenCurlyBrace() = doNegativeTest()

  fun testAdjacent() = doTest()
  fun testBinaryExpressionLHS() = doTest()
  fun testBinaryExpressionRHS() = doTest()
  fun testCaretAfterString() = doTest()
  fun testCaretSomewhereInsideString() = doTest()
  fun testEscapedAndUnescapedOpenCurlyBraces() = doTest()
  fun testJoinedLines() = doTest()
  fun testNestedFString() = doTest()
  fun testRawString() = doTest()
  fun testSingleQuotes() = doTest()
  fun testTripleQuotes() = doTest()

  private fun doTest() = doTest(getHint(), LanguageLevel.PYTHON36)
  private fun doNegativeTest() = runWithLanguageLevel(LanguageLevel.PYTHON36) { doNegativeTest(getHint()) }
  private fun doTestWithPythonOlderThan36() = runWithLanguageLevel(LanguageLevel.PYTHON35) { doNegativeTest(getHint()) }
  private fun getHint() = PyPsiBundle.message("INTN.prefix.fstring")
}