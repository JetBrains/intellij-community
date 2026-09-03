// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.toc

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.Assert
import org.junit.Test

class OmitHeadingFromTocIntentionTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `marker is added to an atx header`() {
    // language=Markdown
    val before = """
    # Some he<caret>ader

    Some text
    """.trimIndent()
    // language=Markdown
    val after = """
    <!-- omit from toc -->
    # Some header

    Some text
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `marker is added to a setext header`() {
    // language=Markdown
    val before = """
    Some he<caret>ader
    ===

    Some text
    """.trimIndent()
    // language=Markdown
    val after = """
    <!-- omit from toc -->
    Some header
    ===

    Some text
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `marker is added above a header that follows a paragraph`() {
    // language=Markdown
    val before = """
    Some text
    # Some he<caret>ader
    """.trimIndent()
    // language=Markdown
    val after = """
    Some text
    <!-- omit from toc -->
    # Some header
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `intention is not available for a header with an inline marker`() {
    // language=Markdown
    val content = """
    # Some he<caret>ader <!-- omit in toc -->
    """.trimIndent()
    doUnavailableTest(content)
  }

  @Test
  fun `intention is not available for a header with a marker above`() {
    // language=Markdown
    val content = """
    <!-- omit from toc -->
    # Some he<caret>ader
    """.trimIndent()
    doUnavailableTest(content)
  }

  @Test
  fun `intention is not available outside of a header`() {
    // language=Markdown
    val content = """
    # Some header

    Some te<caret>xt
    """.trimIndent()
    doUnavailableTest(content)
  }

  private fun doTest(content: String, after: String) {
    myFixture.configureByText("some.md", content)
    val fix = myFixture.findSingleIntention(intentionText)
    myFixture.launchAction(fix)
    myFixture.checkResult(after)
  }

  private fun doUnavailableTest(content: String) {
    myFixture.configureByText("some.md", content)
    val intentions = myFixture.filterAvailableIntentions(intentionText)
    Assert.assertTrue("Intention should not be available", intentions.isEmpty())
  }

  private val intentionText
    get() = MarkdownBundle.message("markdown.omit.heading.from.toc.intention.text")
}
