package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterContentLanguage
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider.Companion.isTomlDelimiterLine
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider.Companion.isYamlDelimiterLine
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterLanguages
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.util.childrenOfType
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.util.MarkdownPsiUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownFrontMatterHeader(type: IElementType): CompositePsiElement(type), PsiLanguageInjectionHost, MarkdownPsiElement {
  override fun isValidHost(): Boolean {
    val children = firstChild.siblings(forward = true, withSelf = true)
    val newlines = children.count { MarkdownPsiUtil.WhiteSpaces.isNewLine(it) }
    return newlines >= 2 && children.find { it.hasType(MarkdownElementTypes.FRONT_MATTER_HEADER_CONTENT) } != null
  }

  override fun updateText(text: String): PsiLanguageInjectionHost {
    return ElementManipulators.handleContentChange(this, text)
  }

  override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> {
    return LiteralTextEscaper.createSimple(this)
  }

  val contentLanguage: FrontMatterContentLanguage
    get() = determineContentLanguage()

  private fun determineContentLanguage(): FrontMatterContentLanguage {
    val delimiters  = childrenOfType(MarkdownElementTypes.FRONT_MATTER_HEADER_DELIMITER).toList()
    check(delimiters.size == 2) { "Unexpected number of delimiters: ${delimiters.size}" }
    val opening = delimiters[0].text
    val closing = delimiters[1].text
    if (isYamlDelimiterLine(opening) && isYamlDelimiterLine(closing)) {
      return FrontMatterLanguages.YAML
    }
    if (isTomlDelimiterLine(opening) && isTomlDelimiterLine(closing)) {
      return FrontMatterLanguages.TOML
    }
    error("Failed to match opening ($opening) and closing ($closing) delimiters to determine content language")
  }

  internal class Manipulator: AbstractElementManipulator<MarkdownFrontMatterHeader>() {
    override fun handleContentChange(element: MarkdownFrontMatterHeader, range: TextRange, content: String): MarkdownFrontMatterHeader? {
      if (content.contains("---")) {
        val textElement = MarkdownPsiElementFactory.createTextElement(element.project, content)
        return if (textElement is MarkdownFrontMatterHeader) {
          element.replace(textElement) as MarkdownFrontMatterHeader
        } else null
      }
      val children = element.firstChild.siblings(forward = true, withSelf = true)
      val contentElement = children.filterIsInstance<MarkdownFrontMatterHeaderContent>().firstOrNull() ?: return null
      val shiftedRange = range.shiftLeft(contentElement.startOffsetInParent)
      val updatedText = shiftedRange.replace(contentElement.text, content)
      contentElement.replaceWithText(updatedText)
      return element
    }
  }
}
