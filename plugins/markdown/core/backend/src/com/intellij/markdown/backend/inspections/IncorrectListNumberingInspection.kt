package com.intellij.markdown.backend.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.StringPattern.newBombedCharSequence
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.text.CharSequenceSubSequence
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.obtainMarkerNumber
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings.ListNumberingType
import org.jetbrains.annotations.ApiStatus

/**
 * Items in ordered lists are expected to use either sequential or constant numbering.
 */
@ApiStatus.Internal
class IncorrectListNumberingInspection: LocalInspectionTool() {
  private val settings by lazy { MarkdownCodeInsightSettings.getInstance() }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (settings.state.listNumberingType == ListNumberingType.PREVIOUS_NUMBER) return PsiElementVisitor.EMPTY_VISITOR

    return object: MarkdownElementVisitor() {
      override fun visitParagraph(paragraph: MarkdownParagraph) {
        if (paragraph.parent !is MarkdownListItem) return
        val document = paragraph.containingFile.viewProvider.document ?: return
        val paragraphStart = paragraph.startOffset
        val text = newBombedCharSequence(
          CharSequenceSubSequence(document.charsSequence, paragraphStart, paragraphStart + paragraph.textLength)
        )
        val matches = UNPARSED_LIST_MARKER.findAll(text).iterator()
        if (!matches.hasNext()) return
        val firstLineStart = DocumentUtil.getLineStartOffset(paragraphStart, document)
        val quoteDepth = document.charsSequence.subSequence(firstLineStart, paragraphStart).count { it == '>' }
        val contentColumn = document.columnAt(paragraphStart)
        for (match in matches) {
          if (match.groupValues[1].count { it == '>' } != quoteDepth) continue
          val number = match.groups[2]!!
          val offset = number.range.first
          val indent = document.columnAt(paragraphStart + offset)
          if (indent !in contentColumn..contentColumn + 3) continue
          val leaf = paragraph.node.findLeafElementAt(offset) ?: continue
          if (leaf.treeParent != paragraph.node) continue
          val actualNumber = number.value.toInt()
          if (actualNumber == 1) return
          holder.registerProblem(
            paragraph,
            MarkdownBundle.message("markdown.incorrectly.numbered.list.item.inspection.text", 1, actualNumber),
            ProblemHighlightType.WEAK_WARNING,
            TextRange(offset, match.range.last + 1),
            ListNumberingFix(sequentially = false)
          )
          return
        }
      }

      override fun visitList(list: MarkdownList) {
        super.visitList(list)
        if (!list.hasType(MarkdownElementTypes.ORDERED_LIST)) {
          return
        }
        if (list.isInlineNestedList()) {
          return
        }
        val listNumberingIsSequential = settings.state.listNumberingType == ListNumberingType.SEQUENTIAL
        val sequentialStartNumber = when {
          list.items.firstOrNull()?.obtainMarkerNumber() == 0 -> 0
          else -> list.continuationStartNumber() ?: 1
        }
        if (list.hasCorrectNumbering(sequentialStartNumber)) {
          return
        }
        val startNumber = if (listNumberingIsSequential) sequentialStartNumber else 1
        val quickFix by lazy { ListNumberingFix(listNumberingIsSequential) }
        for ((index, item) in list.items.withIndex()) {
          val actualNumber = item.obtainMarkerNumber() ?: continue
          val expectedNumber = if (listNumberingIsSequential) startNumber + index else 1
          if (expectedNumber != actualNumber) {
            val markerElement = item.markerElement!!
            holder.registerProblem(
              markerElement,
              MarkdownBundle.message("markdown.incorrectly.numbered.list.item.inspection.text", expectedNumber, actualNumber),
              ProblemHighlightType.WEAK_WARNING,
              quickFix
            )
          }
        }
      }
    }
  }

  private fun Document.columnAt(offset: Int): Int =
    EditorUtil.calcColumnNumber(null, charsSequence, DocumentUtil.getLineStartOffset(offset, this), offset, 4)

  private fun MarkdownList.hasCorrectNumbering(sequentialStartNumber: Int): Boolean {
    val firstNumber = items.firstOrNull()?.obtainMarkerNumber()
    val hasConstantNumbering = items.size > 1 && firstNumber != null && items.all { it.obtainMarkerNumber() == firstNumber }
    val hasSequentialNumbering = items.withIndex().all { (index, item) -> item.obtainMarkerNumber() == sequentialStartNumber + index }
    return hasConstantNumbering || hasSequentialNumbering
  }

  private fun MarkdownList.continuationStartNumber(): Int? {
    val first = items.firstOrNull()?.obtainMarkerNumber()?.takeIf { it > 1 } ?: return null
    val precedingLast = generateSequence(prevSibling) { it.prevSibling }
      .filterIsInstance<MarkdownList>()
      .firstOrNull { it.hasType(MarkdownElementTypes.ORDERED_LIST) }
      ?.items?.lastOrNull()?.obtainMarkerNumber()
    return first.takeIf { precedingLast == first - 1 }
  }

  private fun MarkdownList.isInlineNestedList(): Boolean {
    val parentItem = parent as? MarkdownListItem ?: return false
    val document = containingFile.viewProvider.document ?: return false
    return document.getLineNumber(startOffset) == document.getLineNumber(parentItem.startOffset)
  }

  private class ListNumberingFix(private val sequentially: Boolean): LocalQuickFix {
    override fun getFamilyName(): String = MarkdownBundle.message("markdown.fix.list.items.numbering,quick.fix.text")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      val document = element.containingFile.viewProvider.document
      if (document == null) {
        thisLogger().error("Failed to find document for the quick fix")
        return
      }
      if (element is MarkdownParagraph) {
        val range = descriptor.textRangeInElement ?: return
        val start = element.startOffset + range.startOffset
        val end = CharArrayUtil.shiftForward(document.charsSequence, start, "0123456789")
        document.replaceString(start, end, "1")
      }
      else {
        val list = element.parentOfType<MarkdownList>() ?: return
        list.renumberInBulk(document, recursive = false, restart = true, inWriteAction = false, sequentially = sequentially)
      }
    }
  }
}

private val UNPARSED_LIST_MARKER = Regex("""(?m)^([ \t>]*)([0-9]{1,9})[.)][ \t]+(?=\S)""")