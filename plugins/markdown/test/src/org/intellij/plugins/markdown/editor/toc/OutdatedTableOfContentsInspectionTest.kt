// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.toc

import com.intellij.markdown.backend.inspections.OutdatedTableOfContentsInspection
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.Test

class OutdatedTableOfContentsInspectionTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `blank lines inside toc section are not reported`() {
    doTest("""
      # Header 1

      # Header 2

      <!-- TOC -->

      * [Header 1](#header-1)

      * [Header 2](#header-2)

      <!-- TOC -->
    """)
  }

  @Test
  fun `outdated toc section is reported`() {
    doTest("""
      # Header 1

      # Header 2

      <warning descr="$description"><!-- TOC -->
      * [Header 1](#header-1)
      <!-- TOC --></warning>
    """)
  }

  private fun doTest(content: String) {
    myFixture.enableInspections(OutdatedTableOfContentsInspection())
    myFixture.configureByText("test.md", content.trimIndent())
    myFixture.checkHighlighting()
  }

  private val description
    get() = MarkdownBundle.message("markdown.outdated.table.of.contents.inspection.description")
}
