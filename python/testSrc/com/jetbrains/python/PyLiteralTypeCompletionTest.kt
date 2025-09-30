// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.fixtures.PyTestCase

// PY-40480
@TestDataPath("\$CONTENT_ROOT/../testData/completion/literalType")
class PyLiteralTypeCompletionTest : PyTestCase() {
  fun testInCallExpression() {
    doTestCompletionVariantsContains("inCallExpression.py", "\"1\"", "\"2\"", "\"foo\"", "\"5\"")
  }

  // PY-72661
  fun testNestedParenthesisInCallExpression() {
    myFixture.testCompletionVariants("nestedParenthesisInCallExpression.py", "abb", "bac")
  }

  fun testInKeywordArgument() {
    doTestCompletionVariantsContains("inKeywordArgument.py", "\"3\"", "\"foo\"", "\"5\"")
  }

  // PY-72661
  fun testNestedParenthesisInKeywordArgumentValue() {
    doTestCompletionVariantsContains("nestedParenthesisInKeywordArgumentValue.py", "\"-1\"", "\"foo\"", "\"6\"")
  }

  fun testInSubscriptionExpression() {
    doTestCompletionVariantsContains("inSubscriptionExpression.py", "\"1\"", "\"foo\"", "\"5\"")
  }

  // PY-72661
  fun testNestedParenthesisInSubscriptionExpression() {
    myFixture.testCompletionVariants("nestedParenthesisInSubscriptionExpression.py", "1", "2", "foo", "5")
  }

  fun testInAssigment() {
    doTestCompletionVariantsContains("inAssignment.py", "\"1\"", "\"3\"", "\"foo\"", "\"5\"")
  }

  // PY-72661
  fun testNestedParenthesisInAssigment() {
    doTestCompletionVariantsContains("nestedParenthesisInAssigment.py", "\"0\"", "\"acc\"", "\"5\"")
  }

  fun testInDoubleQuotedString() {
    myFixture.testCompletionVariants("inDoubleQuotedString.py", "bar", "foo")
  }

  fun testInSingleQuotedString() {
    myFixture.testCompletionVariants("inSingleQuotedString.py", "bar", "foo")
  }

  fun testLiteralWithSingleParameter() {
    doTestCompletionVariantsContains("literalWithSingleParameter.py", "\"zzz\"")
  }

  fun testLiteralWithDoubleQuotedStringParameter() {
    doTestCompletionVariantsContains("literalWithDoubleQuotedStringParameter.py", "\"yyy\"")
  }

  fun testLiteralWithSingleQuotedStringParameter() {
    doTestCompletionVariantsContains("literalWithSingleQuotedStringParameter.py", "'xxx'")
  }

  fun testVarargs() {
    myFixture.testCompletionVariants("varargs.py", "aa", "bbb", "zzz")
  }

  fun testKVarargs() {
    myFixture.testCompletionVariants("kvarargs.py", "aa", "bbb", "zzz")
  }

  fun testNestedArgumentLists() {
    myFixture.testCompletionVariants("nestedArgumentLists.py")
  }

  // PY-83039
  fun testNoLiteralVariantsOnQualifiedReferenceInAssignmentValue() {
    doTestCompletionVariantsDoesNotContain("noLiteralVariantsOnQualifiedReferenceInAssignmentValue.py", "\"upper\"")
  }

  // PY-83039
  fun testNoLiteralVariantsOnQualifiedReferenceInCallArgument() {
    doTestCompletionVariantsDoesNotContain("noLiteralVariantsOnQualifiedReferenceInCallArgument.py", "\"upper\"")
  }

  override fun getTestDataPath(): String {
    return super.getTestDataPath() + "/completion/literalType"
  }

  private fun doTestCompletionVariantsContains(fileBefore: String, vararg items: String) {
    val result = myFixture.getCompletionVariants(fileBefore)
    assertNotNull(result)
    assertContainsElements(result!!, *items)
  }

  private fun doTestCompletionVariantsDoesNotContain(fileBefore: String, vararg items: String) {
    val result = myFixture.getCompletionVariants(fileBefore)
    assertNotNull(result)
    assertDoesntContain(result!!, *items)
  }
}