// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.inspections

import com.intellij.markdown.backend.inspections.MarkdownMissingHeaderSpaceInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownMissingHeaderSpaceInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val description = MarkdownBundle.message("markdown.missing.header.space.inspection.description")

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(MarkdownMissingHeaderSpaceInspection())
  }

  @Test
  fun `test header`(): Unit = doFixTest("""
    <warning descr="$description">#Header</warning> text
  """, """
    # Header text
  """)

  @Test
  fun `test level 3 header`(): Unit = doFixTest("""
    <warning descr="$description">###Header</warning>
  """, """
    ### Header
  """)

  @Test
  fun `test punctuation after marker`(): Unit = doFixTest("""
    <warning descr="$description">#!Header</warning>
  """, """
    # !Header
  """)

  @Test
  fun `test list item`(): Unit = doFixTest("""
    - <warning descr="$description">#a</warning>
  """, """
    - # a
  """)

  @Test
  fun `test block quote`(): Unit = doFixTest("""
    > <warning descr="$description">#a</warning>
  """, """
    > # a
  """)

  @Test
  fun `test paragraph continuation`(): Unit = checkHighlighting("""
    text here and there
    #Header
  """)

  @Test
  fun `test no warning`(): Unit = checkHighlighting("""
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
