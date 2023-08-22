// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dataset

import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase


class DataSetQualityTest : SpellcheckerInspectionTestCase() {

  fun `test words spellcheck quality`() {
    val manager = SpellCheckerManager.getInstance(project)

    for (word in Datasets.words) {
      assertFalse("${word.word} should not be misspelled, but it is", manager.hasProblem(word.word))
      for (missp in word.misspellings) {
        assertTrue("${missp} should be misspelled, but it is not", manager.hasProblem(missp))
      }
    }
  }

  fun `test camel-case words spellcheck quality`() {
    val manager = SpellCheckerManager.getInstance(project)

    for (word in Datasets.wordsCamelCase) {
      assertFalse("${word.word} should not be misspelled, but it is", manager.hasProblem(word.word))
      for (missp in word.misspellings) {
        assertTrue("${missp} should be misspelled, but it is not", manager.hasProblem(missp))
      }
    }
  }
}