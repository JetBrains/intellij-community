// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.inspections

import com.intellij.markdown.backend.inspections.MarkdownMissingHeaderSpaceInspection
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.Test

class MarkdownMissingHeaderSpaceInspectionTest : LightPlatformCodeInsightFixture4TestCase() {
  private val description = MarkdownBundle.message("markdown.missing.header.space.inspection.description")

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(MarkdownMissingHeaderSpaceInspection())
  }

  @Test
  fun `test header`() = doFixTest("""
    <warning descr="$description">#Header</warning> text
  """, """
    # Header text
  """)

  @Test
  fun `test level 3 header`() = doFixTest("""
    <warning descr="$description">###Header</warning>
  """, """
    ### Header
  """)

  @Test
  fun `test punctuation after marker`() = doFixTest("""
    <warning descr="$description">#!Header</warning>
  """, """
    # !Header
  """)

  @Test
  fun `test list item`() = doFixTest("""
    - <warning descr="$description">#a</warning>
  """, """
    - # a
  """)

  @Test
  fun `test block quote`() = doFixTest("""
    > <warning descr="$description">#a</warning>
  """, """
    > # a
  """)

  @Test
  fun `test paragraph continuation`() = checkHighlighting("""
    text here and there
    #Header
  """)

  @Test
  fun `test no warning`() = checkHighlighting("""
    Text #moretext

    # Header

    #

    #######text

    #123 fixed

    - #42 Add X

    `#a`

    \#a

    ```
    #a
    ```
  """)

  private fun checkHighlighting(text: String) {
    myFixture.configureByText("some.md", text.trimIndent())
    myFixture.checkHighlighting()
  }

  private fun doFixTest(before: String, after: String) {
    checkHighlighting(before)
    val fixName = MarkdownBundle.message("markdown.missing.header.space.quick.fix.name")
    myFixture.launchAction(myFixture.getAllQuickFixes().single { it.text == fixName })
    myFixture.checkResult(after.trimIndent())
  }
}
