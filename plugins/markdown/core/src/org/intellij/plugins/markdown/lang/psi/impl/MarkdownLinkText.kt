package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownLinkText(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement {
  /**
   * Actual content elements without opening bracket at the start and closing backet at the end.
   */
  val contentElements: Sequence<PsiElement> get() {
    val openBracket = firstChild?.takeIf { it.hasType(MarkdownTokenTypes.LBRACKET) }
    val closeBracket = lastChild?.takeIf { it.hasType(MarkdownTokenTypes.RBRACKET) }
    return children().filterNot { it == openBracket || it == closeBracket }
  }
}
