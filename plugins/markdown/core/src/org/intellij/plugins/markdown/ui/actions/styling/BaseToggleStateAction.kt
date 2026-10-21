package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.editor.runForEachCaret
import org.intellij.plugins.markdown.editor.tables.TableUtils.findTable
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil.getCommonParentOfType
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil.getElementsUnderCaretOrSelection

abstract class BaseToggleStateAction: ToggleAction(), DumbAware {
  protected abstract fun getBoundString(text: CharSequence, selectionStart: Int, selectionEnd: Int): String

  protected open fun getExistingBoundString(text: CharSequence, startOffset: Int): String? {
    return text[startOffset].toString()
  }

  protected abstract fun shouldMoveToWordBounds(): Boolean

  protected abstract val targetNodeType: IElementType

  override fun update(event: AnActionEvent) {
    val editor = findHostEditor(event)
    event.presentation.isEnabled = editor != null
    super.update(event)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isSelected(event: AnActionEvent): Boolean {
    val editor = findHostEditor(event) ?: return false
    val file = event.getData(CommonDataKeys.PSI_FILE) ?: return false
    val document = editor.document
    if (!PsiDocumentManager.getInstance(file.project).isCommitted(document)) return false

    val caretSnapshots = SelectionUtil.obtainCaretSnapshots(this, event, editor).toList()
    val selectionElements = caretSnapshots.map { getElementsUnderCaretOrSelection(file, it.selectionStart, it.selectionEnd) }
    if (shouldIgnoreSelectedElements(selectionElements.asSequence())) {
      event.presentation.isEnabled = false
      return false
    }

    val tableSelections = caretSnapshots.map {
      getTableSelection(file, document, it.selectionStart, it.selectionEnd)
    }
    if (tableSelections.all { it != null }) {
      val selectedRanges = tableSelections.flatMap { it.orEmpty() }
      event.presentation.isEnabled = true
      return selectedRanges.isNotEmpty() && selectedRanges.all { findFormattingParent(file, it) != null }
    }

    val commonParents = caretSnapshots.map {
      findFormattingParent(file, TextRange(it.selectionStart, it.selectionEnd))
    }
    val hasMissingParents = commonParents.any { it == null }
    val hasValidParents = commonParents.any { it != null }
    if (hasMissingParents && hasValidParents) {
      event.presentation.isEnabled = false
      return false
    }
    event.presentation.isEnabled = true
    return !hasMissingParents
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val editor = findHostEditor(event) ?: return
    val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
    WriteCommandAction.writeCommandAction(file.project, file)
      .withName(templatePresentation.text)
      .run<RuntimeException> {
        editor.caretModel.runForEachCaret(reverseOrder = true) { caret ->
          processCaret(file, editor, caret, state)
        }
      }
  }

  private fun processCaret(file: PsiFile, editor: Editor, caret: Caret, state: Boolean) {
    PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
    val tableSelection = getTableSelection(file, editor.document, caret)
    if (tableSelection != null) {
      val removeFormatting = !state || (tableSelection.isNotEmpty() && tableSelection.all { findFormattingParent(file, it) != null })
      for (range in tableSelection.asReversed()) {
        if (removeFormatting) {
          val parent = findFormattingParent(file, range) ?: continue
          removeEmphasisFromSelection(editor.document, range, parent)
        }
        else if (findFormattingParent(file, range) == null) {
          addEmphasisToSelection(editor.document, caret, range)
        }
      }
      return
    }
    if (!state) {
      val parent = findFormattingParent(file, TextRange(caret.selectionStart, caret.selectionEnd))
      if (parent == null) {
        thisLogger().warn("Could not find enclosing element on its destruction")
        return
      }
      removeEmphasisFromSelection(editor.document, TextRange(caret.selectionStart, caret.selectionEnd), parent)
      return
    }
    val parent = findFormattingParent(file, TextRange(caret.selectionStart, caret.selectionEnd))
    if (parent == null) {
      addEmphasisToSelection(editor.document, caret)
    }
    else {
      removeEmphasisFromSelection(editor.document, TextRange(caret.selectionStart, caret.selectionEnd), parent)
    }
  }

  private fun removeEmphasisFromSelection(document: Document, selectionRange: TextRange, parent: PsiElement) {
    val nodeRange = parent.textRange
    val text = document.charsSequence
    val boundString = getExistingBoundString(text, nodeRange.startOffset)
    if (boundString == null) {
      thisLogger().warn("Could not fetch bound string from found node")
      return
    }
    val boundLength = boundString.length

    // Easy case --- selection corresponds to some emph
    if (selectionMatchesNodeContent(selectionRange, nodeRange, boundLength) || selectionCoversContent(selectionRange, parent)) {
      document.deleteString(nodeRange.endOffset - boundLength, nodeRange.endOffset)
      document.deleteString(nodeRange.startOffset, nodeRange.startOffset + boundLength)
      return
    }
    var from = selectionRange.startOffset
    var to = selectionRange.endOffset
    if (shouldMoveToWordBounds()) {
      while (from - boundLength > nodeRange.startOffset && Character.isWhitespace(text[from - 1])) {
        from--
      }
      while (to + boundLength < nodeRange.endOffset && Character.isWhitespace(text[to])) {
        to++
      }
    }
    if (to + boundLength == nodeRange.endOffset) {
      document.deleteString(nodeRange.endOffset - boundLength, nodeRange.endOffset)
    }
    else {
      document.insertString(to, boundString)
    }
    if (from - boundLength == nodeRange.startOffset) {
      document.deleteString(nodeRange.startOffset, nodeRange.startOffset + boundLength)
    }
    else {
      document.insertString(from, boundString)
    }
  }

  private fun selectionMatchesNodeContent(selectionRange: TextRange, nodeRange: TextRange, boundLength: Int): Boolean {
    return nodeRange.startOffset + boundLength == selectionRange.startOffset
           && nodeRange.endOffset - boundLength == selectionRange.endOffset
  }

  private fun selectionCoversContent(selectionRange: TextRange, parent: PsiElement): Boolean {
    val content = PsiTreeUtil.collectElements(parent, ::isFormattingContentElement)
    return content.isNotEmpty() && content.all {
      val elementRange = it.textRange ?: return@all false
      elementRange.startOffset >= selectionRange.startOffset && elementRange.endOffset <= selectionRange.endOffset
    }
  }

  private fun addEmphasisToSelection(
    document: Document,
    caret: Caret,
    selectionRange: TextRange = TextRange(caret.selectionStart, caret.selectionEnd),
  ) {
    var from = selectionRange.startOffset
    var to = selectionRange.endOffset
    val text = document.charsSequence
    if (shouldMoveToWordBounds()) {
      while (from < to && Character.isWhitespace(text[from])) {
        from++
      }
      while (to > from && Character.isWhitespace(text[to - 1])) {
        to--
      }
      if (from == to) {
        from = selectionRange.startOffset
        to = selectionRange.endOffset
      }
    }
    val boundString = getBoundString(text, from, to)
    document.insertString(to, boundString)
    document.insertString(from, boundString)
    if (selectionRange.isEmpty) {
      caret.moveCaretRelatively(boundString.length, 0, false, false)
    }
  }

  private fun getTableSelection(file: PsiFile, document: Document, caret: Caret): List<TextRange>? {
    return getTableSelection(file, document, caret.selectionStart, caret.selectionEnd)
  }

  private fun getTableSelection(file: PsiFile, document: Document, selectionStart: Int, selectionEnd: Int): List<TextRange>? {
    if (selectionStart == selectionEnd) {
      return null
    }
    val selectionRange = TextRange(minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd))
    val table = findTable(file, selectionRange.startOffset) ?: return null
    if (!table.textRange.containsRange(selectionRange.startOffset, selectionRange.endOffset)) {
      return null
    }
    val text = document.charsSequence
    return table.getRows(true)
      .asSequence()
      .flatMap { it.getCells().asSequence() }
      .mapNotNull {
        val cellRange = it.textRange
        var startOffset = maxOf(selectionRange.startOffset, cellRange.startOffset)
        var endOffset = minOf(selectionRange.endOffset, cellRange.endOffset)
        while (startOffset < endOffset && text[startOffset].isWhitespace()) {
          startOffset++
        }
        while (startOffset < endOffset && text[endOffset - 1].isWhitespace()) {
          endOffset--
        }
        if (startOffset < endOffset) TextRange(startOffset, endOffset) else null
      }
      .toList()
  }

  private fun findFormattingParent(file: PsiFile, range: TextRange): PsiElement? {
    val (first, second) = if (range.isEmpty) {
      getElementsUnderCaretOrSelection(file, range.startOffset, range.endOffset)
    }
    else {
      getElementsUnderCaretOrSelection(file, range.startOffset, range.endOffset - 1)
    }
    getCommonParentOfType(first, second, targetNodeType)?.let { return it }
    if (range.isEmpty) {
      return null
    }

    val commonParent = PsiTreeUtil.findCommonParent(first, second) ?: return null
    if (commonParent is PsiFile) {
      return null
    }
    val elements = mutableListOf<PsiElement>()
    collectElementsIntersectingRange(commonParent, range, elements)
    val content = elements.filter { element ->
      isFormattingContentElement(element)
      && range.containsRange(element.textRange.startOffset, element.textRange.endOffset)
    }
    if (content.isEmpty()) {
      return null
    }
    return elements.firstOrNull { element ->
      element.node?.elementType == targetNodeType
      && content.all { PsiTreeUtil.isAncestor(element, it, false) }
    }
  }

  private fun collectElementsIntersectingRange(
    element: PsiElement,
    range: TextRange,
    result: MutableList<PsiElement>,
  ) {
    val elementRange = element.textRange
    if (elementRange != null) {
      if (elementRange.endOffset <= range.startOffset || elementRange.startOffset >= range.endOffset) {
        return
      }
      result.add(element)
    }
    var child = element.firstChild
    while (child != null) {
      collectElementsIntersectingRange(child, range, result)
      child = child.nextSibling
    }
  }

  private fun shouldIgnoreSelectedElements(selectionElements: Sequence<Pair<PsiElement, PsiElement>>): Boolean {
    return elementsToProhibit.any { elementType ->
      elementType != targetNodeType
      && selectionElements.any { getCommonParentOfType(it.first, it.second, elementType) != null }
    }
  }

  private fun findHostEditor(event: AnActionEvent): Editor? {
    val editor = MarkdownActionUtil.findMarkdownEditor(event) ?: return null
    return InjectedLanguageEditorUtil.getTopLevelEditor(editor)
  }

  private fun isFormattingContentElement(element: PsiElement): Boolean {
    val node = element.node ?: return false
    return element.firstChild == null && node.elementType !in formattingMarkerTokenTypes
  }

  companion object {
    private val formattingMarkerTokenTypes = setOf(
      MarkdownTokenTypes.EMPH,
      MarkdownTokenTypes.TILDE,
      MarkdownTokenTypes.EQUALS,
    )

    // If element is prohibited then action's presentation will be disabled
    private val elementsToProhibit = setOf(
      MarkdownElementTypes.CODE_FENCE,
      MarkdownElementTypes.CODE_SPAN,
      MarkdownElementTypes.LINK_TEXT,
      MarkdownElementTypes.LINK_DESTINATION,
      MarkdownElementTypes.TEST_LINK,
      MarkdownElementTypes.AT_PATH,
      MarkdownElementTypes.AUTOLINK,
      MarkdownTokenTypes.GFM_AUTOLINK
    )
  }
}
