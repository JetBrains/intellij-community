// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.jetbrains.python.fixtures.PyTestCase


class PyDictLiteralCompletionTest : PyTestCase() {
  fun testEmptyLiteralsInCallExpressions() {
    assertCompletionContains("\"x\"", "\"y\"")
  }

  fun testEmptyLiteralsInCallExpressionsWithQuotes() {
    assertCompletionContains("x", "y")
  }

  fun testNotEmptyLiteralsInCallExpressions() {
    assertCompletionContains("'y'")
  }

  fun testNotEmptyLiteralsInCallExpressionsWithQuotes() {
    assertCompletionContains("y")
  }

  fun testEmptyLiteralsInAssignments() {
    assertCompletionContains("\"x\"", "\"y\"")
  }

  fun testEmptyLiteralsInAssignmentsWithQuotes() {
    assertCompletionContains("x", "y")
  }

  fun testNotEmptyLiteralsInAssignments() {
    assertCompletionContains("\"y\"")
  }

  fun testNotEmptyLiteralsInAssignmentsWithQuotes() {
    assertCompletionContains("y")
  }

  fun testEmptyLiteralsInReturnStatements() {
    assertCompletionContains("\"x\"", "\"y\"")
  }

  fun testEmptyLiteralsInReturnStatementsWithQuotes() {
    assertCompletionContains("x", "y")
  }

  fun testNotEmptyLiteralsInReturnStatements() {
    assertCompletionContains("'x'")
  }

  fun testNotEmptyLiteralsInReturnStatementsWithQuotes() {
    assertCompletionContains("x")
  }

  fun testNotEmptyLiteralsInAssignmentsWithMultipleTargets() {
    assertCompletionContains("b")
  }

  // PY-42637
  fun testNotEmptyLiteralsInReturnStatementsWithSeveralTypesOfQuotes() {
    assertCompletionContains("\"x\"")
  }

  // PY-42637
  fun testNotEmptyLiteralsInReturnStatementsWithOnlySingleQuotes() {
    assertCompletionContains("'x'")
  }

  // PY-58374
  fun testEditingKeyWithExistingValue() {
    val variants = getVariants()
    assertContainsElements(variants, "coordinateX", "coordinateZ")
    assertDoesntContain(variants, "coordinateY")
  }

  private fun assertCompletionContains(vararg expected: String) {
    val variants = getVariants()
    assertContainsElements(variants, *expected)
  }

  private fun getVariants(): List<String> {
    myFixture.copyDirectoryToProject(getTestName(false), "")
    myFixture.configureByFile("main.py")
    myFixture.completeBasic()
    return myFixture.lookupElementStrings ?: emptyList()
  }


  override fun getTestDataPath(): String {
    return super.getTestDataPath() + "/completion/dictLiteralCompletion/"
  }
}