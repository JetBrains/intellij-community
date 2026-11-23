// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.inspections

import com.intellij.markdown.backend.inspections.MarkdownLazyBlockQuoteContinuationInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@TestApplication
class MarkdownLazyBlockQuoteContinuationInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val description = MarkdownBundle.message("markdown.lazy.block.quote.continuation.inspection.description")

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(MarkdownLazyBlockQuoteContinuationInspection())
  }

  @Test
  fun `test all lazy lines`(): Unit = doFixTest("""
    > line 1
    <weak_warning descr="$description">line 2
    line 3
    line 4</weak_warning>
  """, """
    > line 1
    > line 2
    > line 3
    > line 4
  """)

  @Test
  fun `test nested quote`(): Unit = doFixTest("""
    > > a
    <weak_warning descr="$description">b</weak_warning>
  """, """
    > > a
    > > b
  """)

  @Test
  fun `test quote in list`(): Unit = doFixTest("""
    - > a
      <weak_warning descr="$description">b</weak_warning>
  """, """
    - > a
      > b
  """)

  @Test
  fun `test list in quote`(): Unit = doFixTest("""
    > - a
    <weak_warning descr="$description">b</weak_warning>
  """, """
    > - a
    >   b
  """)

  @Test
  fun `test alert`(): Unit = doFixTest("""
    > [!NOTE]
    > a
    <weak_warning descr="$description">b</weak_warning>
  """, """
    > [!NOTE]
    > a
    > b
  """)

  @Test
  fun `test marker without space`(): Unit = doFixTest("""
    >a
    <weak_warning descr="$description">b</weak_warning>
  """, """
    >a
    >b
  """)

  @Test
  fun `test indented lazy line`(): Unit = doFixTest("""
    > a
    <weak_warning descr="$description">   b</weak_warning>
  """, """
    > a
    > b
  """)

  @Test
  fun `test quote after lazy line`(): Unit = doFixTest("""
    > a
    <weak_warning descr="$description">b</weak_warning>
    > c
  """, """
    > a
    > b
    > c
  """)

  @Test
  fun `test lazy line with inline markup`(): Unit = doFixTest("""
    > a
    <weak_warning descr="$description">**b** [link](https://example.com) `c`</weak_warning>
  """, """
    > a
    > **b** [link](https://example.com) `c`
  """)

  @Test
  fun `test blank line after quote`(): Unit = checkHighlighting("""
    > a

    b
  """)

  @Test
  fun `test empty quote line`(): Unit = checkHighlighting("""
    > a
    >
    b
  """)

  @Test
  fun `test quote ends with heading`(): Unit = checkHighlighting("""
    > # a
    b
  """)

  @Test
  fun `test quote ends with code fence`(): Unit = checkHighlighting("""
    > ```
    > a
    > ```
    b
  """)

  @Test
  fun `test block after quote`(): Unit = checkHighlighting("""
    > a
    - b

    > a
    # b

    > a
    ---

    > a
    > > b
  """)

  @Test
  fun `test new quote after quote in list`(): Unit = checkHighlighting("""
    - > a
    > b
  """)

  @Test
  fun `test lazy list line`(): Unit = checkHighlighting("""
    - a
    b

    > - a
    > b
  """)

  @Test
  fun `test air prompt`() {
    myFixture.configureByText("AirPrompt-${UUID.randomUUID()}.md", "> a\nb")
    myFixture.checkHighlighting()
  }

  private fun checkHighlighting(text: String) {
    myFixture.configureByText("some.md", text.trimIndent())
    myFixture.checkHighlighting()
  }

  private fun doFixTest(before: String, after: String) {
    checkHighlighting(before)
    val fixName = MarkdownBundle.message("markdown.lazy.block.quote.continuation.quick.fix.name")
    myFixture.launchAction(myFixture.getAllQuickFixes().single { it.text == fixName })
    myFixture.checkResult(after.trimIndent())
  }
}
