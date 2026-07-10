// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.intellij.plugins.markdown.MarkdownTestingUtil
import com.intellij.markdown.backend.inspections.IncorrectListNumberingInspection
import com.intellij.markdown.backend.inspections.MarkdownIncorrectTableFormattingInspection
import com.intellij.markdown.backend.inspections.MarkdownNoTableBordersInspection
import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection
import org.intellij.plugins.markdown.model.psi.headers.UnresolvedHeaderReferenceInspection

class MarkdownHighlightingPerformanceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/performance"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(
      IncorrectListNumberingInspection(),
      MarkdownIncorrectTableFormattingInspection(),
      MarkdownNoTableBordersInspection(),
      MarkdownUnresolvedFileReferenceInspection(),
      UnresolvedHeaderReferenceInspection(),
      GrazieInspection.Grammar(), GrazieInspection.Style(), GrazieSpellCheckingInspection()
    )
  }

  @PerformanceUnitTest
  fun `test opening large markdown file performance`() {
    Benchmark.newBenchmark("Opening markdown file") { runHighlightTestForFile("peformance_test_1.md") }
      .setup { PsiManager.getInstance(project).dropPsiCaches() }
      .runAsStressTest()
      .start()
  }

  @Suppress("SameParameterValue")
  private fun runHighlightTestForFile(file: String) {
    myFixture.configureByFile(file)
    myFixture.doHighlighting()
  }
}