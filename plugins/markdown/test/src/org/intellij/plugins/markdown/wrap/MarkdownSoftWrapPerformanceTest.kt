// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.wrap

import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark

@PerformanceUnitTest
class MarkdownSoftWrapPerformanceTest : BasePlatformTestCase() {
  /**
   * Soft wrapping a multi-megabyte markdown document with long lines used to take minutes:
   * `MarkdownLineWrapPositionStrategy` lexed the whole document for every soft wrap position,
   * making the recalculation quadratic in document size.
   */
  fun `test soft wrap recalculation in a large document with long lines`() {
    myFixture.configureByText("large.md", generateLargeDocument())
    val editor = myFixture.editor
    EditorTestUtil.configureSoftWraps(editor, 120)
    val softWrapModel = editor.softWrapModel as SoftWrapModelImpl
    Benchmark.newBenchmark("full soft wrap recalculation in a large markdown document") {
      softWrapModel.applianceManager.reset()
      softWrapModel.applianceManager.recalculateIfNecessary("MarkdownSoftWrapPerformanceTest")
      assertNotEmpty(softWrapModel.registeredSoftWraps)
    }.runAsStressTest().start()
  }

  /**
   * ~1.4 MB of markdown built from long words, so that many soft wrap positions have no whitespace within
   * the quick look-back distance of [com.intellij.openapi.editor.impl.SoftWrapEngine] and the position
   * calculation is delegated to [org.intellij.plugins.markdown.editor.MarkdownLineWrapPositionStrategy].
   */
  private fun generateLargeDocument(): String {
    val words = listOf(
      "internationalization", "misconfiguration", "straightforwardness", "characterization", "acknowledgements",
      "responsibilities", "incompatibilities", "synchronization", "parallelization", "representations",
    )
    return buildString {
      var wordIndex = 0
      repeat(600) { paragraphIndex ->
        repeat(8) { lineIndex ->
          repeat(14) {
            append(words[wordIndex % words.size])
            append(' ')
            wordIndex++
          }
          append("[anchor](https://example.com/generated/path/number/").append(paragraphIndex).append('/').append(lineIndex).append(")\n")
        }
        append('\n')
      }
    }
  }
}
