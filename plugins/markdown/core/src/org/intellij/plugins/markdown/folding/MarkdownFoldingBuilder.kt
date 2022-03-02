package org.intellij.plugins.markdown.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.MarkdownRecursiveElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.*
import org.intellij.plugins.markdown.util.MarkdownPsiUtil.WhiteSpaces.isNewLine
import org.intellij.plugins.markdown.util.MarkdownPsiUtil.processContainer

internal class MarkdownFoldingBuilder: CustomFoldingBuilder(), DumbAware {
  override fun buildLanguageFoldRegions(descriptors: MutableList<FoldingDescriptor>, root: PsiElement, document: Document, quick: Boolean) {
    if (root.language !== root.containingFile.viewProvider.baseLanguage) {
      return
    }
    root.accept(object: MarkdownElementVisitor() {
      override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        element.acceptChildren(this)
      }

      override fun visitList(list: MarkdownList) {
        val parent = list.parent
        addDescriptors(if (parent is MarkdownListItem) parent else list)
        super.visitList(list)
      }

      override fun visitParagraph(paragraph: MarkdownParagraph) {
        val parent = paragraph.parent
        if (parent is MarkdownBlockQuote && parent.childrenOfType<MarkdownParagraph>().size <= 1) {
          return
        }
        addDescriptors(paragraph)
        super.visitParagraph(paragraph)
      }

      override fun visitTable(table: MarkdownTable) {
        addDescriptors(table)
        super.visitTable(table)
      }

      override fun visitBlockQuote(blockQuote: MarkdownBlockQuote) {
        addDescriptors(blockQuote)
        super.visitBlockQuote(blockQuote)
      }

      override fun visitCodeFence(codeFence: MarkdownCodeFence) {
        addDescriptors(codeFence)
        super.visitCodeFence(codeFence)
      }

      private fun addDescriptors(element: MarkdownPsiElement) {
        addDescriptors(element, element.textRange, descriptors, document)
      }
    })
    val headerVisitor = HeaderRegionsBuildingVisitor { header, range -> addDescriptors(header, range, descriptors, document) }
    root.accept(headerVisitor)
    headerVisitor.processLastHeaderIfNeeded()
  }

  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
    val elementType = PsiUtilCore.getElementType(node)
    val explicitName = foldedElementsPresentations[elementType]
    val elementText = StringUtil.shortenTextWithEllipsis(node.text, 30, 5)
    return explicitName?.let { "$it: $elementText" } ?: elementText
  }

  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
    return false
  }

  private class HeaderRegionsBuildingVisitor(private val regionConsumer: (MarkdownPsiElement, TextRange) -> Unit): MarkdownRecursiveElementVisitor() {
    private var lastProcessedHeader: MarkdownHeader? = null

    override fun visitHeader(header: MarkdownHeader) {
      processContainer(header, {}) { nextHeader ->
        val regionEnd = skipNewLinesBackward(nextHeader)
        createRegionIfNeeded(header, regionEnd)
      }
      lastProcessedHeader = header
      super.visitHeader(header)
    }

    fun processLastHeaderIfNeeded() {
      val lastHeader = lastProcessedHeader
      if (lastHeader != null) {
        val lastFileChild = lastHeader.containingFile.lastChild.lastChild
        val regionEnd = when (PsiUtilCore.getElementType(lastFileChild)) {
          MarkdownTokenTypes.EOL -> skipNewLinesBackward(lastFileChild)
          else -> lastFileChild
        }
        val headers = lastHeader.headersHierarchy()
        for (header in headers) {
          createRegionIfNeeded(header, regionEnd)
        }
      }
    }

    private fun PsiElement.headersHierarchy(): Sequence<MarkdownHeader> {
      return sequence {
        val headers = siblings(forward = false, withSelf = true).filterIsInstance<MarkdownHeader>()
        var nextMaxLevel = Int.MAX_VALUE
        for (header in headers) {
          if (nextMaxLevel <= 1) {
            break
          }
          val level = header.level
          if (level < nextMaxLevel) {
            nextMaxLevel = level
            yield(header)
          }
        }
      }
    }

    private fun createRegionIfNeeded(currentHeader: MarkdownHeader, regionEnd: PsiElement?) {
      if (regionEnd != null) {
        val range = TextRange.create(currentHeader.textRange.startOffset, regionEnd.textRange.endOffset)
        regionConsumer.invoke(currentHeader, range)
      }
    }
  }

  companion object {
    private val foldedElementsPresentations = hashMapOf(
      MarkdownElementTypes.ATX_1 to MarkdownBundle.message("markdown.folding.atx.1.name"),
      MarkdownElementTypes.ATX_2 to MarkdownBundle.message("markdown.folding.atx.2.name"),
      MarkdownElementTypes.ATX_3 to MarkdownBundle.message("markdown.folding.atx.3.name"),
      MarkdownElementTypes.ATX_4 to MarkdownBundle.message("markdown.folding.atx.4.name"),
      MarkdownElementTypes.ATX_5 to MarkdownBundle.message("markdown.folding.atx.5.name"),
      MarkdownElementTypes.ATX_6 to MarkdownBundle.message("markdown.folding.atx.6.name"),
      MarkdownElementTypes.ORDERED_LIST to MarkdownBundle.message("markdown.folding.ordered.list.name"),
      MarkdownElementTypes.UNORDERED_LIST to MarkdownBundle.message("markdown.folding.unordered.list.name"),
      MarkdownElementTypes.BLOCK_QUOTE to MarkdownBundle.message("markdown.folding.block.quote.name"),
      MarkdownElementTypes.TABLE to MarkdownBundle.message("markdown.folding.table.name"),
      MarkdownElementTypes.CODE_FENCE to MarkdownBundle.message("markdown.folding.code.fence.name")
    )

    private fun addDescriptors(element: MarkdownPsiElement, range: TextRange, descriptors: MutableList<in FoldingDescriptor>, document: Document) {
      if (document.getLineNumber(range.startOffset) != document.getLineNumber(range.endOffset - 1)) {
        descriptors.add(FoldingDescriptor(element, range))
      }
    }

    private fun skipNewLinesBackward(element: PsiElement?): PsiElement? {
      return element?.siblings(forward = false, withSelf = false)?.firstOrNull { !isNewLine(it) }
    }
  }
}
