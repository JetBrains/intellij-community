package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider
import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import org.intellij.plugins.markdown.lang.psi.impl.*

internal class MarkdownFocusModeProvider: FocusModeProvider {
  private val types = listOf(
    MarkdownList::class,
    MarkdownBlockQuote::class,
    MarkdownTableRow::class,
    MarkdownTable::class,
    MarkdownParagraph::class
  )

  override fun calcFocusZones(psiFile: PsiFile): List<Segment> {
    val traverser = SyntaxTraverser.psiTraverser(psiFile).postOrderDfsTraversal()
    val ranges = traverser.filter { it::class in types }.map { it.textRange }
    return ranges.toMutableList()
  }
}
