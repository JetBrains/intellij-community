package org.intellij.plugins.markdown.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeBlock
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownComment
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderContent
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownInlineLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkText
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownShortReferenceLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCell
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownWrappedAutoLink

object MarkdownPsiFactory {
  @JvmStatic
  fun createElement(node: ASTNode): PsiElement {
    return when (val elementType = node.elementType) {
      MarkdownElementTypes.PARAGRAPH -> MarkdownParagraph(node)
      MarkdownElementTypes.CODE_FENCE -> node as MarkdownCodeFence
      MarkdownElementTypes.FRONT_MATTER_HEADER -> node as MarkdownFrontMatterHeader
      MarkdownElementTypes.INLINE_LINK -> MarkdownInlineLink(node)
      MarkdownElementTypes.LINK_TEXT -> MarkdownLinkText(node)
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
      MarkdownElementTypes.AUTOLINK -> MarkdownWrappedAutoLink(node)
      MarkdownElementTypes.LINK_COMMENT -> MarkdownComment(node)
      else -> when {
        elementType in MarkdownTokenTypeSets.HEADER_CONTENT -> MarkdownHeaderContent(node)
        MarkdownTokenTypeSets.HEADERS.contains(elementType) -> MarkdownHeader(node)
        MarkdownTokenTypeSets.LISTS.contains(elementType) -> MarkdownList(node)
        else -> ASTWrapperPsiElement(node)
      }
    }
  }
}
