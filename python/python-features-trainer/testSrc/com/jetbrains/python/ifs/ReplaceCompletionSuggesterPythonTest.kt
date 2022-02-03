// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ifs

import training.featuresSuggester.FeatureSuggesterTestUtils.chooseCompletionItem
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteTextBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.invokeCodeCompletion
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.FeatureSuggesterTestUtils.typeDelete
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.ReplaceCompletionSuggesterTest

class ReplaceCompletionSuggesterPythonTest : ReplaceCompletionSuggesterTest() {
  override val testingCodeFileName = "PythonCodeExample.py"

  override fun getTestDataPath() = PythonSuggestersTestUtils.testDataPath

  override fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(27, 13)
      deleteAndTypeDot()
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      repeat(8) { typeDelete() }
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(52, 47)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[1])
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 52,
        columnStartIndex = 52,
        lineEndIndex = 52,
        columnEndIndex = 76
      )
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(52, 47)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[1])
      typeAndCommit("123")
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 52,
        columnStartIndex = 56,
        lineEndIndex = 52,
        columnEndIndex = 79
      )
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete with property, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(52, 18)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      repeat(23) { typeDelete() }
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(52, 80)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[1])
      repeat(5) { typeDelete() }
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(52, 18)
      invokeCodeCompletion()
      typeAndCommit("fu")
      val variants = lookupElements ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      repeat(25) { typeDelete() }
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete method call, remove another equal identifier and don't get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(52, 47)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[1])
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 53,
        columnStartIndex = 8,
        lineEndIndex = 53,
        columnEndIndex = 35
      )
    }

    testInvokeLater(myFixture.project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
