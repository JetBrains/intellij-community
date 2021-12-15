// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ifs

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTest
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.NoSuggestion

class LineCommentingSuggesterPythonTest : FeatureSuggesterTest() {
  override val testingCodeFileName = "PythonCodeExample.py"
  override val testingSuggesterId = "Comment with line comment"

  override fun getTestDataPath() = PythonSuggestersTestUtils.testDataPath

  fun `testComment 3 lines in a row and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(3, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(4, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(5, 0)
      typeAndCommit("#")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  fun `testComment 3 lines in different order and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(9, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(11, 7)
      typeAndCommit("#")
      moveCaretToLogicalPosition(10, 2)
      typeAndCommit("#")
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  fun `testComment two lines and one empty line and don't get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(17, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(18, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(19, 0)
      typeAndCommit("#")
    }

    testInvokeLater(myFixture.project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  fun `testComment two lines in a row and one with interval and don't get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(21, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(22, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(24, 0)
      typeAndCommit("#")
    }

    testInvokeLater(myFixture.project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  fun `testComment 3 already commented lines and don't get suggestion`() {
    with(myFixture) {
      insertNewLineAt(25, 8)
      typeAndCommit(
        """        #if True:
            |#    i++
            |#    j--""".trimMargin()
      )

      moveCaretToLogicalPosition(25, 2)
      typeAndCommit("#")
      moveCaretToLogicalPosition(26, 2)
      typeAndCommit("#")
      moveCaretToLogicalPosition(27, 2)
      typeAndCommit("#")
    }

    testInvokeLater(myFixture.project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
