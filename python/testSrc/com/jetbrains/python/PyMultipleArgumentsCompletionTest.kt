// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.codeInsight.lookup.Lookup
import com.jetbrains.python.fixtures.PyTestCase

class PyMultipleArgumentsCompletionTest: PyTestCase() {
  fun testFunctionWithTwoArgs() {
    doTestVariantsContainFinished("x, y")
  }

  fun testFunctionWithThreeArgsTwoSuggested() {
    doTestVariantsContainFinished("y, z")
  }

  fun testSuggestArgumentsForParametersWithDefaultValue() {
    doTestVariantsContainFinished("x, y, z")
  }

  fun testSkipArgumentsForParametersWithDefaultValue() {
    doTestVariantsContainFinished("x, y")
  }

  fun testSkipDefaultValueIfNotFromFunctionContext() {
    doTestVariantsContainFinished("x, y")
  }

  fun testClassConstructor() {
    doTestVariantsContainFinished("x, y, z")
  }

  fun testClassMember() {
    doTestVariantsContainFinished("x, y, z")
  }

  fun testArgumentParameter() {
    doTestVariantsContainFinished("bar, baz")
  }

  fun testListComprehension() {
    doTestVariantsContainFinished("bar, baz")
  }

  fun testOverloads() {
    doTestVariantsContain("a, b", "c, d")
  }

  fun testSingleStarParameter() {
    doTestVariantsContainFinished("a, b=b")
  }

  fun testSlashParameter() {
    doTestVariantsContainFinished("a, b")
  }

  fun testSlashAndSingleStarParameter() {
    doTestVariantsContainFinished("a, b, c=c")
  }

  fun testNotSuggestIfNotEnoughArguments() {
    doTestVariantsNotContain("x, y", "x, y, z")
  }

  fun testNotSuggestIfNotEnoughArgumentsBeforeCaret() {
    doTestVariantsNotContain("x, y", "x, y, z")
  }

  fun testNotSuggestIfNotEnoughArgumentsInTheScopeOfFunction() {
    doTestVariantsNotContain("x, y", "x, y, z")
  }

  fun testNotSuggestIfNotEnoughArgumentsInTheScopeOfNestedFunction() {
    doTestVariantsNotContain("x, y", "x, y, z")
  }

  fun testNotSuggestIfTargetOutOfScopeFunction() {
    doTestVariantsNotContain("x, y", "x, y, z")
  }

  fun testNotSuggestIfHaveArgumentsRight() {
    doTestVariantsNotContain("y, z")
  }

  fun testNotSuggestInKeywordArgument() {
    doTestVariantsNotContain("x, y, z", "y, z")
  }

  fun testNotSuggestKeywordContainer() {
    doTestVariantsNotContain("x, y")
  }

  fun testNotSuggestPositionalContainer() {
    doTestVariantsNotContain("x, y")
  }

  fun testNotSuggestIfOnlyOneVariable() {
    assertEquals(1, doTestByTestName().count { it == "x" })
  }

  fun testNoExceptionIfMoreArgumentsThanParameters() {
    doTestByTestName()
  }

  fun testNoExceptionIfMoreArgumentsWithImplicitThanParameters() {
    doTestByTestName()
  }

  private fun doTestByTestName(): List<String?> {
    val testName = "multipleArgumentsCompletion/${getTestName(true)}"
    myFixture.configureByFile("$testName.py")
    myFixture.completeBasic()
    return myFixture.lookupElementStrings!!
  }

  private fun doTestVariantsContain(vararg elements: String) = assertContainsElements(doTestByTestName(), *elements)

  private fun doTestVariantsContainFinished(element: String) {
    doTestVariantsContain(element)
    myFixture.lookup.currentItem = myFixture.lookupElements.find { it.lookupString == element }
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    myFixture.checkResultByFile("multipleArgumentsCompletion/${getTestName(true)}.after.py")
  }

  private fun doTestVariantsNotContain(vararg elements: String) = assertDoesntContain(doTestByTestName(), *elements)
}