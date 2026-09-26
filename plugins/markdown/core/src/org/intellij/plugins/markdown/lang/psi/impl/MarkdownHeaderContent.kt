package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiWhiteSpace
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType

/**
 * Corresponds to:
 * ```
 * # Some header
 *  ^----------^
 * ```
 * (starting whitespaces are included).
 */
class MarkdownHeaderContent(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement {
  /**
   * Range inside this element, excluding starting whitespaces.
   */
  val nonWhitespaceRange: TextRange
    get() = when (val child = children().filterNot { it is PsiWhiteSpace }.firstOrNull()) {
      null -> TextRange(0, textLength)
      else -> TextRange(child.startOffsetInParent, textLength)
    }

  val isSetextContent: Boolean
    get() = node.hasType(MarkdownTokenTypes.SETEXT_CONTENT)

  val isAtxContent: Boolean
    get() = node.hasType(MarkdownTokenTypes.ATX_CONTENT)

  val relevantContent: String
    get() = nonWhitespaceRange.substring(text)
}
