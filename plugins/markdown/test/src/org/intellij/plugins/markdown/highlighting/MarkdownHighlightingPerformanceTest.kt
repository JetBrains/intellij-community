// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.markdown.backend.inspections.IncorrectListNumberingInspection
import com.intellij.markdown.backend.inspections.MarkdownIncorrectTableFormattingInspection
import com.intellij.markdown.backend.inspections.MarkdownNoTableBordersInspection
import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.intellij.plugins.markdown.model.psi.headers.UnresolvedHeaderReferenceInspection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@StressTestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/performance")
class MarkdownHighlightingPerformanceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @BeforeEach
  fun setUp() {
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
  @Test
  fun `test opening large markdown file performance`() {
    Benchmark.newBenchmark("Opening markdown file") { runHighlightTestForFile("peformance_test_1.md") }
      .setup { PsiManager.getInstance(myFixture.project).dropPsiCaches() }
      .runAsStressTest()
      .start()
  }

  @Suppress("SameParameterValue")
  private fun runHighlightTestForFile(file: String) {
    myFixture.configureByFile(file)
    myFixture.doHighlighting()
  }
}