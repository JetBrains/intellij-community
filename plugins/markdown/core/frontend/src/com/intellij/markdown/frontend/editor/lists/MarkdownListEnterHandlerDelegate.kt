// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.frontend.editor.lists

import com.intellij.codeInsight.editorActions.AutoHardWrapHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.*
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.obtainMarkerNumber
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentRange
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentSpaces
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAt
import org.intellij.plugins.markdown.editor.lists.ListUtils.list
import org.intellij.plugins.markdown.editor.lists.ListUtils.normalizedMarker
import org.intellij.plugins.markdown.editor.lists.MarkdownListItemUnindentHandler
import org.intellij.plugins.markdown.lang.psi.impl.*
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.intellij.plugins.markdown.util.MarkdownPsiUtil

/**
 * This handler does three things to the current list item:
 *   - if it has no contents and is in a top-level list, it is removed
 *   - if it has no contents and is in a nested list, it's nesting level is decreased (as if it was unindented)
 *   - otherwise, a new list item is created next to the current one
 *
 * This handler will also adjust list item numbers based on the [MarkdownCodeInsightSettings.State.renumberListsOnType] setting.
 */
@ExperimentalStdlibApi
internal class MarkdownListEnterHandlerDelegate: EnterHandlerDelegate {
  private var emptyItem: String? = null

  private val codeInsightSettings
    get() = MarkdownCodeInsightSettings.getInstance().state

  override fun invokeInsideIndent(newLineCharOffset: Int, editor: Editor, dataContext: DataContext): Boolean {
    val project = editor.project ?: return false
    if (!codeInsightSettings.smartEnterAndBackspace) {
      return false
    }
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    return file is MarkdownFile
  }

  override fun preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffset: Ref<Int>,
    caretAdvance: Ref<Int>,
    dataContext: DataContext,
    originalHandler: EditorActionHandler?
  ): EnterHandlerDelegate.Result {
    emptyItem = null // if last post-processing ended with an exception, clear the state
    if (!codeInsightSettings.smartEnterAndBackspace) {
      return EnterHandlerDelegate.Result.Continue
    }
    if (file !is MarkdownFile || isInCodeFence(caretOffset.get(), file)) {
      return EnterHandlerDelegate.Result.Continue
    }

    if (DataManager.getInstance().loadFromDataContext(dataContext, AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY) == true) {
      editor.putUserData(AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY, true)
      return EnterHandlerDelegate.Result.Continue
    }

    val document = editor.document
    PsiDocumentManager.getInstance(file.project).commitDocument(document)

    val line = document.getLineNumber(caretOffset.get())
    if (line > 0) {
      val indentRange = document.getLineIndentRange(line)
      val insideIndent = !indentRange.isEmpty && caretOffset.get() <= indentRange.endOffset
      val prevLineBlank = document.getLineIndentRange(line - 1).endOffset == document.getLineEndOffset(line - 1)
      // this also works inside a block quote, since '>' is also treated as an indent for lists
      if (insideIndent && prevLineBlank) {
        // otherwise an item will be created, but it will be too far to be a part of the current list
        return EnterHandlerDelegate.Result.Continue
      }
    }

    val item = file.getListItemAt(caretOffset.get(), document) ?: return EnterHandlerDelegate.Result.Continue
    if (item.children.isEmpty()) {
      handleEmptyItem(item, editor, file, originalHandler, dataContext)
      caretOffset.set(editor.caretModel.offset)
      return EnterHandlerDelegate.Result.Stop
    }

    val blockQuote = MarkdownPsiUtil.findNonWhiteSpacePrevSibling(file, caretOffset.get())?.parentOfType<MarkdownBlockQuote>()
    if (blockQuote != null && item.isAncestor(blockQuote)) {
      return EnterHandlerDelegate.Result.Continue
    }

    val itemLine = document.getLineNumber(item.startOffset)
    val markerElement = item.markerElement!!
    val indentWithMakerRange = document.getLineIndentRange(itemLine).union(markerElement.textRange)

    if (indentWithMakerRange.contains(caretOffset.get())) {
      caretOffset.set(markerElement.endOffset)
    }

    val indentSpaces = document.getLineIndentSpaces(itemLine, file) ?: ""
    emptyItem = indentSpaces + item.normalizedMarker
    return EnterHandlerDelegate.Result.Default
  }

  private fun isInCodeFence(caretOffset: Int, file: PsiFile): Boolean {
    if (caretOffset == 0) {
      return false
    }
    val element = file.findElementAt(caretOffset - 1) ?: return false
    val fence = element.parentOfType<MarkdownCodeFence>(withSelf = true)
    return fence != null
  }

  private fun handleEmptyItem(item: MarkdownListItem, editor: Editor, file: PsiFile, originalHandler: EditorActionHandler?, dataContext: DataContext) {
    val markerElement = item.markerElement!!
    val document = editor.document

    if (item.parentOfType<MarkdownListItem>(false) == null) {
      val backspaceHandler = MarkdownListMarkerBackspaceHandlerDelegate()
      val markerEnd = markerElement.endOffset
      editor.caretModel.moveToOffset(markerEnd)
      val char = editor.document.charsSequence[markerEnd - 1]

      backspaceHandler.beforeCharDeleted(char, file, editor)
      runWriteAction {
        document.deleteString(markerEnd - 1, markerEnd)
      }
      backspaceHandler.charDeleted(char, file, editor)
    }
    else {
      val unindentHandler = MarkdownListItemUnindentHandler(originalHandler)
      unindentHandler.execute(editor, editor.caretModel.currentCaret, dataContext)
    }
  }

  override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
    if (editor.getUserData(AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY) == true) {
      editor.putUserData(AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY, null)
      return EnterHandlerDelegate.Result.Continue
    }
    val emptyItem = emptyItem ?: return EnterHandlerDelegate.Result.Continue // for smart cast

    val document = editor.document
    EditorModificationUtil.insertStringAtCaret(editor, emptyItem)
    PsiDocumentManager.getInstance(file.project).commitDocument(document)
    val item = (file as MarkdownFile).getListItemAt(editor.caretModel.offset, document)!!
    val listNumberingType = codeInsightSettings.listNumberingType
    if (codeInsightSettings.renumberListsOnType && listNumberingType != MarkdownCodeInsightSettings.ListNumberingType.PREVIOUS_NUMBER) {
      // Will fix numbering in a whole list
      item.list.renumberInBulk(document, recursive = false, restart = false, sequentially = listNumberingType == MarkdownCodeInsightSettings.ListNumberingType.SEQUENTIAL)
    } else {
      // Will only fix current item based on previous one
      val previousItem = item.siblings(forward = false, withSelf = false).filterIsInstance<MarkdownListItem>().firstOrNull()
      val previousNumber = previousItem?.obtainMarkerNumber()
      if (previousNumber != null) {
        val marker = item.markerElement as? MarkdownListNumber
        val currentNumber = when (listNumberingType) {
          MarkdownCodeInsightSettings.ListNumberingType.SEQUENTIAL -> previousNumber + 1
          MarkdownCodeInsightSettings.ListNumberingType.ONES -> 1
          MarkdownCodeInsightSettings.ListNumberingType.PREVIOUS_NUMBER -> previousNumber
        }
        marker?.replaceWithOtherNumber(currentNumber)
      }
    }

    // it is possible that there will be no pre-processing before next post-processing, see IDEA-270501 and EnterInLineCommentHandler
    this.emptyItem = null
    return EnterHandlerDelegate.Result.Stop
  }

  companion object {
    private fun MarkdownListNumber.replaceWithOtherNumber(number: Int): MarkdownListNumber {
      return replaceWithText("$number$delimiter ") as MarkdownListNumber
    }
  }
}
