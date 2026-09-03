// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.breadcrumbs

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.breadcrumbs.Crumb
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader

class MarkdownBreadcrumbsProviderTest : BasePlatformTestCase() {
  fun `test breadcrumbs follow heading hierarchy`() {
    myFixture.configureByText(
      "test.md",
      """
        # Heading
        ## Subheading
        ### Heading 3-1
        Text<caret>
        ### Heading 3-2
      """.trimIndent()
    )

    assertEquals(
      listOf("Heading", "Subheading", "Heading 3-1"),
      myFixture.getBreadcrumbsAtCaret().map(Crumb::getText),
    )
  }

  fun `test breadcrumbs are empty before the first heading`() {
    myFixture.configureByText("test.md", "Text<caret>\n# Heading")

    assertEmpty(myFixture.getBreadcrumbsAtCaret())
  }

  fun `test image is hidden from breadcrumb text`() {
    myFixture.configureByText("test.md", "# ![Icon](icon.png) Heading\nText<caret>")

    assertEquals(listOf("Heading"), myFixture.getBreadcrumbsAtCaret().map(Crumb::getText))
  }

  fun `test setext heading is included in breadcrumbs`() {
    myFixture.configureByText("test.md", "Heading\n=======\nText<caret>")

    assertEquals(listOf("Heading"), myFixture.getBreadcrumbsAtCaret().map(Crumb::getText))
  }

  fun `test skipped heading level is included in breadcrumbs`() {
    myFixture.configureByText("test.md", "# Heading\n### Subheading\nText<caret>")

    assertEquals(listOf("Heading", "Subheading"), myFixture.getBreadcrumbsAtCaret().map(Crumb::getText))
  }

  fun `test consecutive headings have the same parent`() {
    myFixture.configureByText("test.md", "# Heading\n## First\nText<caret>\n## Second")

    assertEquals(listOf("Heading", "First"), myFixture.getBreadcrumbsAtCaret().map(Crumb::getText))
  }

  fun `test nested heading keeps outer heading`() {
    myFixture.configureByText("test.md", "# Heading\n> ## Quoted\n> Text<caret>")

    assertEquals(listOf("Heading", "Quoted"), myFixture.getBreadcrumbsAtCaret().map(Crumb::getText))
  }

  fun `test heading in previous list item is included in breadcrumbs`() {
    myFixture.configureByText("test.md", "- # Heading\n- Text<caret>")

    assertEquals(listOf("Heading"), myFixture.getBreadcrumbsAtCaret().map(Crumb::getText))
  }

  fun `test outer heading is not hidden by nested heading in previous list item`() {
    myFixture.configureByText("test.md", "- # A\n  ### B\n- ### C\n  Text<caret>")

    assertEquals(listOf("A", "C"), myFixture.getBreadcrumbsAtCaret().map(Crumb::getText))
  }

  fun `test markdown headers are not accepted as sticky elements`() {
    myFixture.configureByText("test.md", "# Heading\nText<caret>")

    val provider = MarkdownBreadcrumbsProvider()
    val header = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownHeader::class.java) ?: error("No Markdown header")
    assertFalse(provider.acceptStickyElement(header))
  }
}
