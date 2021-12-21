package org.intellij.plugins.markdown.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.*

object MarkdownPsiFactory {
  @JvmStatic
  fun createElement(node: ASTNode): PsiElement {
    return when (val elementType = node.elementType) {
      MarkdownElementTypes.PARAGRAPH -> MarkdownParagraph(node)
      MarkdownElementTypes.CODE_FENCE -> node as MarkdownCodeFence
      MarkdownElementTypes.IMAGE -> MarkdownImage(node)
      MarkdownElementTypes.LIST_ITEM -> MarkdownListItem(node)
      MarkdownElementTypes.BLOCK_QUOTE -> MarkdownBlockQuote(node)
      MarkdownElementTypes.SHORT_REFERENCE_LINK -> MarkdownShortReferenceLink(node)
      MarkdownElementTypes.LINK_DEFINITION -> MarkdownLinkDefinition(node)
      MarkdownElementTypes.LINK_DESTINATION -> MarkdownLinkDestination(node)
      MarkdownElementTypes.LINK_LABEL -> MarkdownLinkLabel(node)
      MarkdownElementTypes.CODE_BLOCK -> MarkdownCodeBlock(node)
      MarkdownElementTypes.TABLE -> MarkdownTable(node)
      MarkdownElementTypes.TABLE_ROW, MarkdownElementTypes.TABLE_HEADER -> MarkdownTableRow(node)
      MarkdownElementTypes.TABLE_CELL -> MarkdownTableCell(node)
      else -> when {
        MarkdownTokenTypeSets.HEADERS.contains(elementType) -> MarkdownHeader(node)
        MarkdownTokenTypeSets.LISTS.contains(elementType) -> MarkdownList(node)
        else -> ASTWrapperPsiElement(node)
      }
    }
  }
}
