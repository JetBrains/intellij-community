// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ifs

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTestUtils.focusEditor
import training.featuresSuggester.FeatureSuggesterTestUtils.logicalPositionToOffset
import training.featuresSuggester.FeatureSuggesterTestUtils.performFindInFileAction
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FileStructureSuggesterTest
import training.featuresSuggester.NoSuggestion

class FileStructureSuggesterPythonTest : FileStructureSuggesterTest() {
  override val testingCodeFileName = "PythonCodeExample.py"

  override fun getTestDataPath() = PythonSuggestersTestUtils.testDataPath

  override fun `testFind field and get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(16, 0)
      performFindInFileAction("field", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  fun `testFind global variable and get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(0, 0)
      performFindInFileAction("bcd", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testFind method and get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(0, 0)
      performFindInFileAction("functi", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  fun `testFind class and get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(0, 0)
      performFindInFileAction("clazz", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testFind function parameter and don't get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(0, 0)
      performFindInFileAction("aaa", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  override fun `testFind local variable declaration and don't get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(35, 0)
      performFindInFileAction("strin", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  override fun `testFind variable usage and don't get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(10, 0)
      performFindInFileAction("aaa", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  override fun `testFind method usage and don't get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(14, 0)
      performFindInFileAction("function", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  override fun `testFind type usage and don't get suggestion`() {
    with(myFixture) {
      val fromOffset = logicalPositionToOffset(31, 9)
      performFindInFileAction("Claz", fromOffset)
      focusEditor()
    }

    testInvokeLater(myFixture.project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
