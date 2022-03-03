// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ifs

import training.featuresSuggester.FeatureSuggesterTestUtils.copyCurrentSelection
import training.featuresSuggester.FeatureSuggesterTestUtils.cutBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.pasteFromClipboard
import training.featuresSuggester.FeatureSuggesterTestUtils.selectBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.IntroduceVariableSuggesterTest

/**
 * Note: when user is declaring variable and it's name starts with any language keyword suggestion will not be thrown
 */
class IntroduceVariableSuggesterPythonTest : IntroduceVariableSuggesterTest() {
  override val testingCodeFileName = "PythonCodeExample.py"

  override fun getTestDataPath() = PythonSuggestersTestUtils.testDataPath

  override fun `testIntroduce expression from IF and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 3, columnStartIndex = 2, lineEndIndex = 3, columnEndIndex = 12)
      insertNewLineAt(3)
      typeAndCommit("eee =")
      pasteFromClipboard()
      moveCaretToLogicalPosition(4, 2)
      typeAndCommit(" eee")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce full expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 27, columnStartIndex = 25, lineEndIndex = 27, columnEndIndex = 57)
      insertNewLineAt(27, 8)
      typeAndCommit("value = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(28, 25)
      typeAndCommit("value")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce part of expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 27, columnStartIndex = 56, lineEndIndex = 27, columnEndIndex = 46)
      insertNewLineAt(27, 8)
      typeAndCommit("val = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(28, 46)
      typeAndCommit("val")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce part of string expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 36, columnStartIndex = 18, lineEndIndex = 36, columnEndIndex = 37)
      insertNewLineAt(36, 4)
      typeAndCommit("str = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(37, 18)
      typeAndCommit("str")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce full expression from return statement and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 37, columnStartIndex = 11, lineEndIndex = 37, columnEndIndex = 42)
      insertNewLineAt(37, 4)
      typeAndCommit("output = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(38, 11)
      typeAndCommit("output")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce expression from method body using copy and backspace and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(
        lineStartIndex = 27,
        columnStartIndex = 42,
        lineEndIndex = 27,
        columnEndIndex = 56
      )
      copyCurrentSelection()
      selectBetweenLogicalPositions(
        lineStartIndex = 27,
        columnStartIndex = 42,
        lineEndIndex = 27,
        columnEndIndex = 56
      )
      deleteSymbolAtCaret()
      insertNewLineAt(27, 8)
      typeAndCommit("out=")
      pasteFromClipboard()
      moveCaretToLogicalPosition(28, 42)
      typeAndCommit("out")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  fun `testIntroduce part of string declaration expression and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 35, columnStartIndex = 13, lineEndIndex = 35, columnEndIndex = 24)
      insertNewLineAt(35, 4)
      typeAndCommit("str = ")
      pasteFromClipboard()
      typeAndCommit(";")
      moveCaretToLogicalPosition(36, 13)
      typeAndCommit("str")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }
}
