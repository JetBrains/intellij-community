package org.intellij.plugins.markdown.lang.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.WhitespacesAndCommentsBinder
import com.intellij.lang.WhitespacesBinders
import com.intellij.openapi.progress.ProgressManager
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.plugins.markdown.lang.MarkdownElementType
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.util.textRange

internal class PsiBuilderFillingVisitor(
  private val builder: PsiBuilder,
  startingFromFileLevel: Boolean
): RecursiveVisitor() {
  private var seenFirstMarker = startingFromFileLevel

  private val HEADERS = MarkdownTokenTypeSets.HEADERS.types.map { MarkdownElementType.markdownType(it) }.toSet()

  private fun ASTNode.isEmptyCell(): Boolean {
    return type == GFMTokenTypes.CELL && textRange.isEmpty
  }

  override fun visitNode(node: ASTNode) {
    ProgressManager.checkCanceled()
    val type = node.type
    if (node.isEmptyCell()) {
      // We encountered two separators without cell node between them
      // In that case underlying parser should've emitted a cell node,
      // but it is not capable of producing nodes with empty range.
      // So we fix it here by manually adding an empty cell node.
      ensureBuilderInPosition(node.startOffset)
      builder.mark().done(org.intellij.plugins.markdown.lang.MarkdownElementTypes.TABLE_CELL)
      //builder.mark().done(MarkdownTokenTypes.TABLE_SEPARATOR)
    }
    if (node is LeafASTNode) {
      /* a hack for the link reference definitions:
       * they are being parsed independent of link references and
       * the link titles and urls are tokens instead of composite elements
       */
      if (type != MarkdownElementTypes.LINK_LABEL && type != MarkdownElementTypes.LINK_DESTINATION) {
        return
      }
    }
    if (type == MarkdownElementTypes.MARKDOWN_FILE) {
      ensureBuilderInPosition(node.startOffset)
      super.visitNode(node)
      ensureBuilderInPosition(node.endOffset)
      return
    }
    ensureBuilderInPosition(node.startOffset)
    val marker = builder.mark()
    if (!seenFirstMarker) {
      seenFirstMarker = true
      marker.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER)
    }
    else if (type in HEADERS) {
      //Headers should eat leading whitespaces on the line to effectively occupy whole line
      marker.setCustomEdgeTokenBinders(leadingWhitespaces(), null)
    }

    super.visitNode(node)
    ensureBuilderInPosition(node.endOffset)
    if (type is MarkdownCollapsableElementType) {
      marker.collapse(MarkdownElementType.platformType(type))
      return
    }
    marker.done(MarkdownElementType.platformType(type))
  }

  private fun ensureBuilderInPosition(position: Int) {
    //Advance to position and maybe skip whitespaces
    while (builder.currentOffset < position) {
      builder.advanceLexer()
    }
  }

  private fun leadingWhitespaces(): WhitespacesAndCommentsBinder {
    return WhitespacesAndCommentsBinder { tokens, _, getter ->
      var i = 0
      while (i < tokens.size && (tokens[i] != MarkdownTokenTypes.WHITE_SPACE || getter.get(i).isNotBlank())) {
        i++
      }
      i
    }
  }
}
