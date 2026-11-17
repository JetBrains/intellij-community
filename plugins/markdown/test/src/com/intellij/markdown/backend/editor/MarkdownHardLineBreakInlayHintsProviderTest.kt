// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.doTestProvider
import org.junit.jupiter.api.Test

@TestApplication
internal class MarkdownHardLineBreakInlayHintsProviderTest {
  private val projectFixture = codeInsightProjectFixture()
  private val fixture by codeInsightFixture(projectFixture)

  @Test
  fun `trailing spaces get the soft wrap sign`() {
    doTest("first  /*<# ⤦ #>*/\nsecond")
  }

  @Test
  fun `backslash gets no sign`() {
    doTest("first\\\nsecond")
  }

  private fun doTest(expected: String) {
    doTestProvider(fixture, "test.md", expected, MarkdownHardLineBreakInlayHintsProvider())
  }
}
