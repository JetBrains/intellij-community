package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.options.advanced.AdvancedSettings
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getParentOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleTagProvider
import org.intellij.plugins.markdown.lang.MarkdownElementType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader.Companion.createUniqueAnchorText
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader.Companion.replaceEntities
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HeaderGeneratingProvider(headerTag: String): SimpleTagProvider(headerTag) {
  override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val anchorText = buildUniqueAnchorText(node, text)
    if (anchorText == null) {
      return super.openTag(visitor, text, node)
    }
    visitor.consumeTagOpen(node, tagName, "id=\"$anchorText\"")
  }

  companion object {
    private val whitespaces = setOf(
      MarkdownTokenTypes.EOL,
      MarkdownTokenTypes.WHITE_SPACE
    )

    private fun findContentHolder(node: ASTNode): ASTNode? {
      return node.children().find {
        it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT
      }
    }

    fun buildAnchorText(node: ASTNode, fileText: String): String? {
      val contentHolder = findContentHolder(node) ?: return null
      val children = contentHolder.children().dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
      val text = buildString {
        var count = 0
        for (child in children) {
          if (child.type == MarkdownTokenTypes.WHITE_SPACE) {
            append(" ")
            continue
          }
          when (child.type) {
            MarkdownElementTypes.IMAGE -> append("")
            MarkdownElementTypes.INLINE_LINK -> processInlineLink(child, fileText)
            else -> append(child.getTextInNode(fileText))
          }
          count += 1
        }
      }
      val replaced = replaceEntities(text).lowercase().replace(MarkdownHeader.garbageRegex, "").replace(" ", "-")

      return when {
        AdvancedSettings.getBoolean("markdown.squash.multiple.dashes.in.header.anchors") -> replaced.replace(Regex("-{2,}"), "-")
        else -> replaced
      }
    }

    private fun buildUniqueAnchorText(node: ASTNode, fileText: String): String? {
      val anchorText = buildAnchorText(node, fileText) ?: return null
      val number = calculateUniqueNumber(node, fileText, anchorText)
      return createUniqueAnchorText(anchorText, number)
    }

    private fun calculateUniqueNumber(node: ASTNode, fileText: String, rawAnchorText: String): Int {
      val file = node.getParentOfType(MarkdownElementTypes.MARKDOWN_FILE) ?: return 0
      val headers = file.traverse().filter { MarkdownElementType.isHeaderElementType(it.type) }
      val sameHeaders = headers.filter { buildAnchorText(it, fileText) == rawAnchorText }
      return sameHeaders.takeWhile { it != node }.count()
    }

    private fun obtainLinkTextElements(node: ASTNode): Sequence<ASTNode> {
      val textHolder = node.children().find { it.type == MarkdownElementTypes.LINK_TEXT } ?: return emptySequence()
      val openBracket = textHolder.firstChild?.takeIf { it.type == MarkdownTokenTypes.LBRACKET }
      val closeBracket = textHolder.lastChild?.takeIf { it.type == MarkdownTokenTypes.RBRACKET }
      return textHolder.children().filterNot { it == openBracket || it == closeBracket }
    }

    private fun StringBuilder.processInlineLink(node: ASTNode, fileText: String) {
      val contentElements = obtainLinkTextElements(node)
      val withoutWhitespaces = contentElements.filterNot { it.type in whitespaces }
      withoutWhitespaces.joinTo(this, separator = " ") {
        when (it.type) {
          MarkdownElementTypes.IMAGE -> ""
          else -> it.getTextInNode(fileText)
        }
      }
    }
  }
}
