package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.editor.runForEachCaret
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
    val editor = MarkdownActionUtil.findMarkdownEditor(event)
    event.presentation.isEnabled = editor != null
    super.update(event)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isSelected(event: AnActionEvent): Boolean {
    if (MarkdownActionUtil.findMarkdownEditor(event) == null) {
      return false
    }
    val file = event.getData(CommonDataKeys.PSI_FILE) ?: return false
    val caretSnapshots = SelectionUtil.obtainCaretSnapshots(this, event)?.asSequence() ?: return false
    val selectionElements = caretSnapshots.map { getElementsUnderCaretOrSelection(file, it.selectionStart, it.selectionEnd) }
    val commonParents = selectionElements.map { (left, right) -> getCommonParentOfType(left, right, targetNodeType) }
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
    val editor = MarkdownActionUtil.findMarkdownEditor(event) ?: return
    val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
    runWriteAction {
      executeCommand(file.project, templatePresentation.text) {
        editor.caretModel.runForEachCaret(reverseOrder = true) { caret ->
          processCaret(file, editor, caret, state)
        }
      }
    }
  }

  private fun processCaret(file: PsiFile, editor: Editor, caret: Caret, state: Boolean) {
    val (first, second) = getElementsUnderCaretOrSelection(file, caret)
    if (!state) {
      val parent = getCommonParentOfType(first, second, targetNodeType)
      if (parent == null) {
        thisLogger().warn("Could not find enclosing element on its destruction")
        return
      }
      removeEmphasisFromSelection(editor.document, caret, parent.textRange)
      return
    }
    val parent = PsiTreeUtil.findCommonParent(first, second)
    if (parent.elementType !in elementsToIgnore) {
      addEmphasisToSelection(editor.document, caret)
    }
  }

  private fun removeEmphasisFromSelection(document: Document, caret: Caret, nodeRange: TextRange) {
    val text = document.charsSequence
    val boundString = getExistingBoundString(text, nodeRange.startOffset)
    if (boundString == null) {
      thisLogger().warn("Could not fetch bound string from found node")
      return
    }
    val boundLength = boundString.length

    // Easy case --- selection corresponds to some emph
    if (nodeRange.startOffset + boundLength == caret.selectionStart
        && nodeRange.endOffset - boundLength == caret.selectionEnd) {
      document.deleteString(nodeRange.endOffset - boundLength, nodeRange.endOffset)
      document.deleteString(nodeRange.startOffset, nodeRange.startOffset + boundLength)
      return
    }
    var from = caret.selectionStart
    var to = caret.selectionEnd
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

  private fun addEmphasisToSelection(document: Document, caret: Caret) {
    var from = caret.selectionStart
    var to = caret.selectionEnd
    val text = document.charsSequence
    if (shouldMoveToWordBounds()) {
      while (from < to && Character.isWhitespace(text[from])) {
        from++
      }
      while (to > from && Character.isWhitespace(text[to - 1])) {
        to--
      }
      if (from == to) {
        from = caret.selectionStart
        to = caret.selectionEnd
      }
    }
    val boundString = getBoundString(text, from, to)
    document.insertString(to, boundString)
    document.insertString(from, boundString)
    if (caret.selectionStart == caret.selectionEnd) {
      caret.moveCaretRelatively(boundString.length, 0, false, false)
    }
  }

  companion object {
    private val elementsToIgnore = setOf(
      MarkdownElementTypes.LINK_DESTINATION,
      MarkdownElementTypes.AUTOLINK,
      MarkdownTokenTypes.GFM_AUTOLINK
    )
  }
}
