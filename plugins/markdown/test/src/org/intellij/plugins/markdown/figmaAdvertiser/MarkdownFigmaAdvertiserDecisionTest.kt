package org.intellij.plugins.markdown.figmaAdvertiser

import com.intellij.markdown.figmaAdvertiser.FigmaAdvertiserRegistry
import com.intellij.markdown.figmaAdvertiser.FigmaConnectPluginSuggestionProvider
import com.intellij.markdown.figmaAdvertiser.containsFigmaUrl
import com.intellij.markdown.figmaAdvertiser.isMarkdownSuggestionFile
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The questions the banner answers before it draws anything, asked without a project and without a
 * Swing component.
 */
class MarkdownFigmaAdvertiserDecisionTest {

  @Test
  fun `a design link in prose is a Figma link`() {
    containsFigmaUrl("The spec is at https://www.figma.com/design/AbC123/Checkout?node-id=1-2 .").shouldBeTrue()
  }

  @Test
  fun `a file link and a proto link are Figma links`() {
    containsFigmaUrl("[spec](https://figma.com/file/AbC123/Checkout)").shouldBeTrue()
    containsFigmaUrl("[flow](http://www.figma.com/proto/AbC123/Checkout)").shouldBeTrue()
  }

  /** The word is not the link. A page that talks about Figma has not shared a design. */
  @Test
  fun `the word figma alone is not a Figma link`() {
    containsFigmaUrl("We moved the design system to Figma last year.").shouldBeFalse()
    containsFigmaUrl("See https://www.figma.com/ for the product.").shouldBeFalse()
    containsFigmaUrl("Read https://www.figma.com/blog/why-we-did-it").shouldBeFalse()
  }

  /** A host that merely ends in the name belongs to somebody else. */
  @Test
  fun `a look-alike host is not a Figma link`() {
    containsFigmaUrl("https://notfigma.com/design/AbC123/Checkout").shouldBeFalse()
  }

  @Test
  fun `the extensions the Markdown file type declares are the ones that are looked into`() {
    isMarkdownSuggestionFile("/src/docs/README.md").shouldBeTrue()
    isMarkdownSuggestionFile("/src/docs/README.markdown").shouldBeTrue()
    isMarkdownSuggestionFile("/src/docs/rules.mdc").shouldBeTrue()
    // A file system keeps the case a user typed.
    isMarkdownSuggestionFile("/src/docs/README.MD").shouldBeTrue()
  }

  @Test
  fun `another extension is not looked into`() {
    isMarkdownSuggestionFile("/src/docs/README.txt").shouldBeFalse()
    isMarkdownSuggestionFile("/src/App.tsx").shouldBeFalse()
    // A directory named like the extension is not a file with it.
    isMarkdownSuggestionFile("/src/md/notes").shouldBeFalse()
  }

  /**
   * The default is written twice, and a running IDE reads the declaration rather than the constant.
   * Read here from the descriptor's own text, so a change to one of the two fails this.
   */
  @Test
  fun `the registry key default matches the descriptor declaration`() {
    val descriptor = FigmaConnectPluginSuggestionProvider::class.java
      .getResourceAsStream("/intellij.markdown.figmaAdvertiser.xml")
      .shouldNotBeNull()

    val declaration = descriptor.use { DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(it) }
      .getElementsByTagName("registryKey")
      .let { keys -> (0 until keys.length).map { keys.item(it) as Element } }
      .single { it.getAttribute("key") == FigmaAdvertiserRegistry.KEY_ADVERTISER_ENABLED }

    declaration.getAttribute("defaultValue") shouldBe FigmaAdvertiserRegistry.ENABLED_BY_DEFAULT.toString()
  }
}
