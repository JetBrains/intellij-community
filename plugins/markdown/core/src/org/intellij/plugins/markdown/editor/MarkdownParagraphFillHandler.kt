package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.editorActions.fillParagraph.ParagraphFillHandler
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal class MarkdownParagraphFillHandler: ParagraphFillHandler() {
  override fun isAvailableForElement(element: PsiElement?): Boolean {
    return element?.parentOfType<MarkdownParagraph>(withSelf = true) != null
  }

  override fun atWhitespaceToken(element: PsiElement?): Boolean {
    return super.atWhitespaceToken(element) || element?.hasType(MarkdownTokenTypes.WHITE_SPACE) == true
  }

  override fun isBunchOfElement(element: PsiElement?): Boolean {
    return element?.hasType(MarkdownTokenTypes.TEXT) == false
  }

  override fun isAvailableForFile(psiFile: PsiFile?): Boolean {
    val file = psiFile ?: return false
    return file.language.isMarkdownLanguage()
  }
}
