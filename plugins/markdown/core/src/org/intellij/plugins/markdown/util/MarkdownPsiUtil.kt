package org.intellij.plugins.markdown.util

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal object MarkdownPsiUtil {
  /** Check if node is on a top-level -- meaning its parent is root of file   */
  fun isTopLevel(node: ASTNode) = node.treeParent.hasType(MarkdownTokenTypeSets.MARKDOWN_FILE)

  object WhiteSpaces {
    /** Check if element is new line */
    @JvmStatic
    fun isNewLine(element: PsiElement): Boolean {
      return element.hasType(MarkdownTokenTypeSets.WHITE_SPACES) && element.text == "\n"
    }

    /** Check if element is whitespace -- not a new line, not `>` blockquote */
    @JvmStatic
    fun isWhiteSpace(element: PsiElement): Boolean {
      return element.hasType(MarkdownTokenTypeSets.WHITE_SPACES) && element.text.all { it.isWhitespace() && it != '\n' }
    }
  }

  fun findNonWhiteSpacePrevSibling(file: PsiFile, offset: Int): PsiElement? {
    var offset = offset
    while (offset > 0) {
      val element = file.findElementAt(offset)
      if (element == null) {
        offset--
        continue
      }
      if (!MarkdownTokenTypeSets.WHITE_SPACES.contains(element.node.elementType)) {
        return element
      }
      val newOffset = element.textOffset
      if (newOffset < offset) {
        offset = newOffset
      }
      else {
        offset--
      }
    }
    return null
  }
}
