// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.featureTraining.ifs

import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import training.featuresSuggester.FeatureSuggesterTestUtils.focusEditor
import training.featuresSuggester.FeatureSuggesterTestUtils.logicalPositionToOffset
import training.featuresSuggester.FeatureSuggesterTestUtils.performFindInFileAction
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.featureSuggesterFixture

@Subsystems.IDE
@Components.FeatureTrainer
@Layers.Functional
@TestApplication
class FileStructureSuggesterPythonTest {
  private val suggester by featureSuggesterFixture(PythonSuggestersTestUtils.testDataPath, "PythonCodeExample.py", "File structure")

  @Test
  fun `testFind field and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(16, 0)
      performFindInFileAction("field", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testFind global variable and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(0, 0)
      performFindInFileAction("bcd", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testFind method and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(0, 0)
      performFindInFileAction("functi", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testFind class and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(0, 0)
      performFindInFileAction("clazz", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testFind function parameter and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(0, 0)
      performFindInFileAction("aaa", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testFind local variable declaration and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(35, 0)
      performFindInFileAction("strin", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testFind variable usage and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(10, 0)
      performFindInFileAction("aaa", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testFind method usage and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(14, 0)
      performFindInFileAction("function", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testFind type usage and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(31, 9)
      performFindInFileAction("Claz", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
