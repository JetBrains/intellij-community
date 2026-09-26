// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser

import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.intellij.plugins.markdown.MarkdownTestingUtil
import java.io.File

class MarkdownParserPerformanceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/performance"

  @PerformanceUnitTest
  fun `test parsing a heavily edited large markdown file performance`() {
    val document = fragmentedDocument()
    var attempt = 0

    Benchmark.newBenchmark("Parsing a heavily edited large markdown file") {
      MarkdownParserManager.parseContent(document.immutableCharSequence)
    }
      .setup { document.insertString((attempt++ * CACHE_BUSTER_STEP) % document.textLength, " ") }
      .attempts(ATTEMPT_COUNT)
      .warmupIterations(WARMUP_COUNT)
      .runAsStressTest()
      .start()
  }

  private fun fragmentedDocument(): DocumentImpl {
    val part = File(testDataPath, SOURCE_FILE).readText()
    val text = buildString {
      repeat(COPY_COUNT) { append(part) }
    }
    val document = DocumentImpl(text, true)
    repeat(EDIT_COUNT) { index ->
      val offset = (index * EDIT_STEP) % document.textLength
      document.insertString(offset, "x")
      document.deleteString(offset, offset + 1)
    }
    return document
  }

  private companion object {
    private const val SOURCE_FILE = "peformance_test_1.md"

    /** The source file holds 35 KB, so 14 copies give about 500 KB. */
    private const val COPY_COUNT = 14
    private const val EDIT_COUNT = 28_000
    private const val EDIT_STEP = 97
    private const val CACHE_BUSTER_STEP = 8191
    private const val ATTEMPT_COUNT = 20
    private const val WARMUP_COUNT = 5
  }
}
