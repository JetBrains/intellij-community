// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.links

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.junit.Assert
import org.junit.Test

class ConvertToReferenceLinkIntentionTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `single link becomes a reference link`() {
    // language=Markdown
    val before = """
    See the [JetBra<caret>ins](https://jetbrains.com) site.
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [JetBrains][jetbrains] site.

    [jetbrains]: https://jetbrains.com
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `every link with the same destination becomes a reference link`() {
    // language=Markdown
    val before = """
    Try [IntelliJ I<caret>DEA](https://jetbrains.com/idea) today.

    The [same IDE](https://jetbrains.com/idea) is free for students.
    """.trimIndent()
    // language=Markdown
    val after = """
    Try [IntelliJ IDEA][intellij-idea] today.

    The [same IDE][intellij-idea] is free for students.

    [intellij-idea]: https://jetbrains.com/idea
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `title moves into the definition`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com "The docs") for details.
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] for details.

    [docs]: https://example.com "The docs"
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `an existing definition for the destination is reused`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    [home]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][home] for details.

    [home]: https://example.com
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a definition with the same title is reused`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com "The docs") for details.

    [home]: https://example.com

    [docs-page]: https://example.com "The docs"
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs-page] for details.

    [home]: https://example.com

    [docs-page]: https://example.com "The docs"
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a hidden definition is not reused`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://second.example.com) for details.

    [home]: https://first.example.com

    [home]: https://second.example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] for details.

    [home]: https://first.example.com

    [home]: https://second.example.com

    [docs]: https://second.example.com
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a footnote definition is not reused`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) and a note[^note].

    [^note]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] and a note[^note].

    [^note]: https://example.com

    [docs]: https://example.com
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a comment is not reused as a definition`() {
    // language=Markdown
    val before = """
    See the [se<caret>ction](#section) below.

    [//]: #section (a note)
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [section][section] below.

    [//]: #section (a note)

    [section]: #section
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a definition with a comment label is reused`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    [//]: https://example.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][//] for details.

    [//]: https://example.com
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `an equivalent destination shares one definition`() {
    // language=Markdown
    val before = """
    See the [gu<caret>ide](<a page.md>) and the [same page](a%20page.md).
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [guide][guide] and the [same page][guide].

    [guide]: a%20page.md
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `a destination with a space gives a definition that parses`() {
    // language=Markdown
    val before = """
    See the [gu<caret>ide](<a page.md>) for details.
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [guide][guide] for details.

    [guide]: a%20page.md
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `the definition lands before an unclosed code fence`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    ```kotlin
    val answer = 42
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] for details.

    [docs]: https://example.com

    ```kotlin
    val answer = 42
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `the definition lands before an unclosed html block`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    <!--
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] for details.

    [docs]: https://example.com

    <!--
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `the definition lands before an unclosed script block`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    <script>
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] for details.

    [docs]: https://example.com

    <script>
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `the definition lands after a closed code fence`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    ```kotlin
    val answer = 42
    ```
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] for details.

    ```kotlin
    val answer = 42
    ```

    [docs]: https://example.com
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `the definition lands after an unclosed fence inside a block quote`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    > ```kotlin
    > val answer = 42
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] for details.

    > ```kotlin
    > val answer = 42

    [docs]: https://example.com
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `the definition lands after an unclosed html block inside a block quote`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    > <!--
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] for details.

    > <!--

    [docs]: https://example.com
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `a taken label gets a numeric suffix`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com) for details.

    [docs]: https://other.com
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs-1] for details.

    [docs]: https://other.com

    [docs-1]: https://example.com
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a link with another title stays inline`() {
    // language=Markdown
    val before = """
    See the [do<caret>cs](https://example.com "One") and the [notes](https://example.com "Two").
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [docs][docs] and the [notes](https://example.com "Two").

    [docs]: https://example.com "One"
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `the caret lands inside the new label`() {
    // language=Markdown
    val before = """
    See the [JetBra<caret>ins](https://jetbrains.com) site.
    """.trimIndent()
    // language=Markdown
    val after = """
    See the [JetBrains][<caret>jetbrains] site.

    [jetbrains]: https://jetbrains.com
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `the label falls back to the destination host`() {
    // language=Markdown
    val before = """
    See [](https://exam<caret>ple.com/page) for details.
    """.trimIndent()
    // language=Markdown
    val after = """
    See [https://example.com/page][example-com] for details.

    [example-com]: https://example.com/page
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `a fallback link text escapes a bracket`() {
    // language=Markdown
    val before = """
    See [](fo<caret>o]bar) for details.
    """.trimIndent()
    // language=Markdown
    val after = """
    See [foo\]bar][foo-bar] for details.

    [foo-bar]: foo]bar
    """.trimIndent()
    doTest(before, after)
    assertSingleDefinition()
  }

  @Test
  fun `intention is not available on plain text`() {
    // language=Markdown
    doUnavailableTest("Some te<caret>xt")
  }

  @Test
  fun `intention is not available on an image`() {
    // language=Markdown
    doUnavailableTest("![lo<caret>go](https://example.com/logo.png)")
  }

  @Test
  fun `intention is not available on a reference link`() {
    // language=Markdown
    val content = """
    See the [do<caret>cs][docs] for details.

    [docs]: https://example.com
    """.trimIndent()
    doUnavailableTest(content)
  }

  @Test
  fun `intention is not available on a link inside an image`() {
    // language=Markdown
    doUnavailableTest("![[te<caret>xt](https://example.com)](https://example.com/logo.png)")
  }

  @Test
  fun `intention is not available on an autolink`() {
    // language=Markdown
    doUnavailableTest("<https://exam<caret>ple.com>")
  }

  @Test
  fun `intention is not available inside a code span`() {
    // language=Markdown
    doUnavailableTest("Use `[do<caret>cs](https://example.com)` in the text.")
  }

  @Test
  fun `intention is not available on a link text with a bracket`() {
    // language=Markdown
    doUnavailableTest("See [docs [d<caret>raft]](https://example.com) for details.")
  }

  @Test
  fun `intention is not available on a title that a definition cannot hold`() {
    val title = "a".repeat(1200)
    // language=Markdown
    doUnavailableTest("""See the [do<caret>cs](https://example.com "$title") for details.""")
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

  private fun assertSingleDefinition() {
    val definitions = PsiTreeUtil.findChildrenOfType(myFixture.file, MarkdownLinkDefinition::class.java)
    Assert.assertEquals("The appended definition should parse as a link definition", 1, definitions.size)
  }

  private val intentionText
    get() = MarkdownBundle.message("markdown.convert.to.reference.link.intention.text")
}
