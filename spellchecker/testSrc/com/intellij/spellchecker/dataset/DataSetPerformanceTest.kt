// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.dataset

import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase
import com.intellij.testFramework.PlatformTestUtil


class DataSetPerformanceTest: SpellcheckerInspectionTestCase() {

  fun `test missp spellcheck performance`() {
    val manager = SpellCheckerManager.getInstance(project)
    val total = Datasets.missp.flatMap { it.misspellings + it.word }.size

    PlatformTestUtil.startPerformanceTest("highlight ${total} words in missp", 1750) {
      for (word in Datasets.missp) {
        manager.hasProblem(word.word)
        for (missp in word.misspellings) {
          manager.hasProblem(missp)
        }
      }
    }.assertTiming()
  }

  fun `test words spellcheck performance`() {
    val manager = SpellCheckerManager.getInstance(project)
    val total = Datasets.words.flatMap { it.misspellings + it.word }.size

    PlatformTestUtil.startPerformanceTest("highlight ${total} words in words", 200) {
      for (word in Datasets.words) {
        manager.hasProblem(word.word)
        for (missp in word.misspellings) {
          manager.hasProblem(missp)
        }
      }
    }.assertTiming()
  }


  fun `test camel-case words spellcheck performance`() {
    val manager = SpellCheckerManager.getInstance(project)
    val total = Datasets.wordsCamelCase.flatMap { it.misspellings + it.word }.size

    PlatformTestUtil.startPerformanceTest("highlight ${total} words in camel-case", 500) {
      for (word in Datasets.wordsCamelCase) {
        manager.hasProblem(word.word)
        for (missp in word.misspellings) {
          manager.hasProblem(missp)
        }
      }
    }.assertTiming()
  }
}