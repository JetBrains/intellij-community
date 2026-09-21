package org.intellij.plugins.markdown.model

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.model.psi.impl.targetSymbols
import org.intellij.plugins.markdown.model.psi.labels.LinkLabelSymbol
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LinkLabelFindUsagesTest: BasePlatformTestCase() {
  @Test
  fun `shortcut and collapsed reference links are usages`() {
    val content = """
      [heading]
      [heading][]
      [heading][heading]

      [heading  title]
      [heading  title][]
      [heading  title][heading  title]

      [HE<caret>ADING]: #heading
      [HEADING title]: #title
    """.trimIndent()
    myFixture.configureByText("some.md", content)

    val usages = myFixture.testFindUsagesUsingAction()

    assertEquals(3, usages.size)
  }

  @Test
  fun `normalized label is a usage`() {
    val content = """
      [heading  title]
      [heading title][]
      [heading  title][heading title]

      [HE<caret>ADING title]: #title
    """.trimIndent()
    myFixture.configureByText("some.md", content)

    assertEquals(3, myFixture.testFindUsagesUsingAction().size)
  }

  @Test
  fun `normalized references use the first matching definition`() {
    val content = """
      [ heading  title ]
      [ heading title ][]
      [text][ heading title ]

      [ HE<caret>ADING title ]: https://example.com
      [ heading title ]: https://example.org
    """.trimIndent()
    myFixture.configureByText("some.md", content)

    assertEquals(3, myFixture.testFindUsagesUsingAction().size)
  }

  @Test
  fun `all image reference forms are usages`() {
    myFixture.configureByText("some.md", """
      ![label]
      ![label][]
      ![alt][label]
      ![LABEL]
      [![label]](https://example.org)
      [![label][]](https://example.org)
      [![alt][label]](https://example.org)
      [![label]][outer]
      [![label][]][outer]
      [![alt][label]][outer]

      [la<caret>bel]: https://example.com/image.png
      [outer]: https://example.org
    """.trimIndent())

    assertEquals(10, myFixture.testFindUsagesUsingAction().size)
  }

  @Test
  fun `image alternate text does not add usages`() {
    myFixture.configureByText("some.md", """
      [label]
      [label][]
      ![label](https://example.com/image.png)
      ![alt [label]](https://example.com/image.png)
      ![label][other]

      [la<caret>bel]: https://example.com
      [other]: https://example.org
    """.trimIndent())

    assertEquals(2, myFixture.testFindUsagesUsingAction().size)
  }

  @Test
  fun `a definition in a block quote precedes a definition in a list`() {
    myFixture.configureByText("some.md", """
      [label]
      [text][label]

      > [la<caret>bel]: https://example.com

      - A list item

        [label]: https://example.org
    """.trimIndent())

    assertEquals(2, myFixture.testFindUsagesUsingAction().size)
  }

  @Test
  fun `a definition in a list precedes a definition in a block quote`() {
    myFixture.configureByText("some.md", """
      [label]
      [text][label]

      - A list item

        [la<caret>bel]: https://example.com

      > [label]: https://example.org
    """.trimIndent())

    assertEquals(2, myFixture.testFindUsagesUsingAction().size)
  }

  @Test
  fun `a comment wrapper does not hide a later definition`() {
    myFixture.configureByText("some.md", """
      [/<caret>/]

      [//]: # (A comment)
      [//]: https://example.com
    """.trimIndent())

    val target = targetSymbols(myFixture.file, myFixture.caretOffset).filterIsInstance<LinkLabelSymbol>().single()
    assertEquals(myFixture.file.text.lastIndexOf("[//]") + 1, target.range.startOffset)
  }

  @Test
  fun `definitions in code and HTML comments do not hide a later definition`() {
    myFixture.configureByText("some.md", """
      ```markdown
      [label]: https://example.org
      ```

          [label]: https://example.org

      <!--
      [label]: https://example.org
      -->

      [label]
      [label][]

      [la<caret>bel]: https://example.com
    """.trimIndent())

    assertEquals(2, myFixture.testFindUsagesUsingAction().size)
  }

  @Test
  fun `resolution updates after the first definition is removed`() {
    myFixture.configureByText("some.md", """
      [text][la<caret>bel]

      [label]: https://example.com
      [LABEL]: https://example.org
    """.trimIndent())
    fun resolvedLabel(): String = targetSymbols(myFixture.file, myFixture.caretOffset).filterIsInstance<LinkLabelSymbol>().single().text

    assertEquals("label", resolvedLabel())

    val document = myFixture.editor.document
    val start = document.text.indexOf("[label]:")
    val end = document.text.indexOf("[LABEL]:")
    WriteCommandAction.runWriteCommandAction(project) {
      document.deleteString(start, end)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    assertEquals("LABEL", resolvedLabel())
  }

  @Test
  fun `short and collapsed footnote references are not link label symbols`() {
    val content = """
      [^note]
      [^note][]

      [^note]: footnote text
    """.trimIndent()
    myFixture.configureByText("some.md", content)

    for (offset in listOf(
      myFixture.file.text.indexOf("[^note]"),
      myFixture.file.text.indexOf("[^note][]"),
      myFixture.file.text.indexOf("[^note]:")
    )) {
      val symbols = targetSymbols(myFixture.file, offset + 2)
      assertTrue(symbols.none { it is LinkLabelSymbol })
    }
  }

}
