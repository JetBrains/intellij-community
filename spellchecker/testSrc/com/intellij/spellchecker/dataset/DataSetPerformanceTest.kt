// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.dataset

import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark


class DataSetPerformanceTest: SpellcheckerInspectionTestCase() {

  fun `test misspelled words spellcheck performance`() {
    val manager = SpellCheckerManager.getInstance(project)
    val total = Datasets.misspelling.flatMap { it.misspellings + it.word }.size

    Benchmark.newBenchmark("highlight ${total} misspelled words") {
      for (word in Datasets.misspelling) {
        manager.hasProblem(word.word)
        for (misspelling in word.misspellings) {
          manager.hasProblem(misspelling)
        }
      }
    }.start()
  }

  fun `test words spellcheck performance`() {
    val manager = SpellCheckerManager.getInstance(project)
    val total = Datasets.words.flatMap { it.misspellings + it.word }.size

    Benchmark.newBenchmark("highlight ${total} words in words") {
      for (word in Datasets.words) {
        manager.hasProblem(word.word)
        for (misspelling in word.misspellings) {
          manager.hasProblem(misspelling)
        }
      }
    }.start()
  }


  fun `test camel-case words spellcheck performance`() {
    val manager = SpellCheckerManager.getInstance(project)
    val total = Datasets.wordsCamelCase.flatMap { it.misspellings + it.word }.size

    Benchmark.newBenchmark("highlight ${total} words in camel-case") {
      for (word in Datasets.wordsCamelCase) {
        manager.hasProblem(word.word)
        for (misspelling in word.misspellings) {
          manager.hasProblem(misspelling)
        }
      }
    }.start()
  }
}