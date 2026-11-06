// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.featureTraining.ifs

import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.featureSuggesterFixture

/**
 * Use `intellij.python.tests` module as a classpath to run this test locally
 */
@Subsystems.IDE
@Components.FeatureTrainer
@Layers.Functional
@TestApplication
class LineCommentingSuggesterPythonTest {
  private val suggester by featureSuggesterFixture(PythonSuggestersTestUtils.testDataPath, "PythonCodeExample.py", "Comment with line comment")

  @Test
  fun `testComment 3 lines in a row and get suggestion`(): Unit = suggester.runTest {
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

  @Test
  fun `testComment 3 lines in different order and get suggestion`(): Unit = suggester.runTest {
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

  @Test
  fun `testComment two lines and one empty line and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(17, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(18, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(19, 0)
      typeAndCommit("#")
    }

    testInvokeLater(myFixture.project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testComment two lines in a row and one with interval and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(21, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(22, 0)
      typeAndCommit("#")
      moveCaretToLogicalPosition(24, 0)
      typeAndCommit("#")
    }

    testInvokeLater(myFixture.project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testComment 3 already commented lines and don't get suggestion`(): Unit = suggester.runTest {
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
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
