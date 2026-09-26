// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.links

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.Assert
import org.junit.Test

class ConvertToInlineLinkIntentionTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `full reference link becomes an inline link`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `short reference link becomes an inline link`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs] for details.

    [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `every link with the same label becomes an inline link`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    The [same page][docs] lists every option.

    [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.

    The [same page](https://example.com) lists every option.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a collapsed reference link keeps the definition`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    The [docs][] list every option.

    [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.

    The [docs][] list every option.

    [docs]: https://example.com
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `title moves into the inline link`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    [docs]: https://example.com "The docs"
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com "The docs") for details.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a label with another case still resolves`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][DOCS] for details.

    [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a label with another space run still resolves`() {
    // language=Markdown
    val before = """
    See the [release  no<caret>tes][release  notes] here.

    [release notes]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [release  notes](https://example.com) here.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `the blank line of a definition in the middle is kept once`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    [docs]: https://example.com

    That is all.
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.

    That is all.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a definition alone in a block quote takes the quote with it`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    > [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a definition alone in a list takes the list with it`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    - [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a list keeps the item that holds no definition`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    - The first item.
    - [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.

    - The first item.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a block quote keeps the line that follows the definition`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    > [docs]: https://example.com
    > A note.
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.

    > A note.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a block quote keeps the line that precedes the definition`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    > A note.
    >
    > [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.

    > A note.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a block quote keeps a code block that looks like a marker`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    >     >
    >
    > [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.

    >     >
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a definition inside a list in a block quote still resolves`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][docs] for details.

    > - [docs]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `intention is not available on a footnote reference`() {
    // language=Markdown
    val content = """
    See the docs[^no<caret>te].

    [^note]: The note.
    """.trimIndent()
    doUnavailableTest(content)
  }

  @Test
  fun `intention is not available on a comment label`() {
    // language=Markdown
    val content = """
    See the [se<caret>ction][//] below.

    [//]: #section (a note)
    """.trimIndent()
    doUnavailableTest(content)
  }

  @Test
  fun `a definition with a comment label still converts`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs][//] for details.

    [//]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs](https://example.com) for details.
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `intention is not available for a label with no definition`() {
    // language=Markdown
    doUnavailableTest("See the [do<caret>cs][missing] for details.")
  }

  @Test
  fun `intention is not available on a label with other outer spaces`() {
    // language=Markdown
    val content = """
    See the [do<caret>cs][ docs ] for details.

    [docs]: https://example.com
    """.trimIndent()
    doUnavailableTest(content)
  }

  @Test
  fun `intention is not available on a destination that no inline link holds`() {
    // language=Markdown
    val content = """
    See the [do<caret>cs][docs] for details.

    [docs]: foo(bar
    """.trimIndent()
    doUnavailableTest(content)
  }

  @Test
  fun `intention is not available on a collapsed reference link`() {
    // language=Markdown
    val content = """
    See the [do<caret>cs][] for details.

    [docs]: https://example.com
    """.trimIndent()
    doUnavailableTest(content)
  }

  @Test
  fun `intention is not available on an inline link`() {
    // language=Markdown
    doUnavailableTest("See the [do<caret>cs](https://example.com) for details.")
  }

  @Test
  fun `intention is not available on plain text`() {
    // language=Markdown
    doUnavailableTest("Some te<caret>xt")
  }

  private fun doTest(content: String, after: String) {
    myFixture.configureByText("some.md", content)
    val fix = myFixture.findSingleIntention(intentionText)
    myFixture.checkPreviewAndLaunchAction(fix)
    myFixture.checkResult(after)
  }

  private fun doUnavailableTest(content: String) {
    myFixture.configureByText("some.md", content)
    val intentions = myFixture.filterAvailableIntentions(intentionText)
    Assert.assertTrue("Intention should not be available", intentions.isEmpty())
  }

  private val intentionText
    get() = MarkdownBundle.message("markdown.convert.to.inline.link.intention.text")
}
