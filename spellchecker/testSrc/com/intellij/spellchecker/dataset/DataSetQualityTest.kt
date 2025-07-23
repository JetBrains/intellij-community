// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dataset

import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.dataset.Datasets.WordWithMisspellings
import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MAX_WORD_LENGTH = 32

class DataSetQualityTest : SpellcheckerInspectionTestCase() {

  fun `test words spellcheck quality`() {
    doSpellcheckingTest(Datasets.words)
  }

  fun `test camel-case words spellcheck quality`() {
    doSpellcheckingTest(Datasets.wordsCamelCase)
  }

  private fun doSpellcheckingTest(dataset: List<WordWithMisspellings>) {
    val manager = SpellCheckerManager.getInstance(project)

    dataset.forEach { word ->
      assertFalse(manager.hasProblem(word.word), "${word.word} should not be misspelled, but it is")
      assertLessThanOrEqualMaxWordLength("${word.word} exceeds max word length $MAX_WORD_LENGTH", word.word.length)

      word.misspellings.forEach { misspelling ->
        assertTrue(manager.hasProblem(misspelling), "$misspelling should be misspelled, but it is")
        assertLessThanOrEqualMaxWordLength("$misspelling exceeds max word length $MAX_WORD_LENGTH", misspelling.length)
      }
    }
  }
}

private fun assertLessThanOrEqualMaxWordLength(message: String, actual: Int) {
  assertTrue(actual <= MAX_WORD_LENGTH, message)
}
