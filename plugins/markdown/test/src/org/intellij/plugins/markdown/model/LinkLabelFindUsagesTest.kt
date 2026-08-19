package org.intellij.plugins.markdown.model

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
