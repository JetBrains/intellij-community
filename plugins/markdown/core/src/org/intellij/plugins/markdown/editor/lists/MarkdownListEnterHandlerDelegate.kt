// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

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
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.DocumentUtil
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentRange
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAt
import org.intellij.plugins.markdown.editor.lists.ListUtils.list
import org.intellij.plugins.markdown.editor.lists.ListUtils.normalizedMarker
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * This handler does three things to the current list item:
 *   - if it has no contents and is in a top-level list, it is removed
 *   - if it has no contents and is in a nested list, it's nesting level is decreased (as if it was unindented)
 *   - otherwise, a new list item is created next to the current one
 */
@ExperimentalStdlibApi
internal class MarkdownListEnterHandlerDelegate : EnterHandlerDelegate {

  private var marker: String? = null

  override fun invokeInsideIndent(newLineCharOffset: Int, editor: Editor, dataContext: DataContext): Boolean {
    val project = editor.project ?: return false
    return MarkdownSettings.getInstance(project).isEnhancedEditingEnabled &&
           PsiDocumentManager.getInstance(project).getPsiFile(editor.document) is MarkdownFile
  }

  override fun preprocessEnter(file: PsiFile,
                               editor: Editor,
                               caretOffset: Ref<Int>,
                               caretAdvance: Ref<Int>,
                               dataContext: DataContext,
                               originalHandler: EditorActionHandler?): EnterHandlerDelegate.Result {
    marker = null // if last post-processing ended with an exception, clear the state
    if (file !is MarkdownFile || isInBlockQuoteOrCodeFence(caretOffset.get(), file)
        || !MarkdownSettings.getInstance(file.project).isEnhancedEditingEnabled) {
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
      val prevLineBlank = DocumentUtil.isLineEmpty(document, line - 1)
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

    val itemLine = document.getLineNumber(item.startOffset)
    val markerElement = item.markerElement!!
    val indentWithMakerRange = document.getLineIndentRange(itemLine).union(markerElement.textRange)

    if (indentWithMakerRange.contains(caretOffset.get())) {
      caretOffset.set(markerElement.endOffset)
    }

    marker = item.normalizedMarker
    return EnterHandlerDelegate.Result.Default
  }

  private fun isInBlockQuoteOrCodeFence(caretOffset: Int, file: PsiFile): Boolean {
    if (caretOffset == 0) return false

    return file.findElementAt(caretOffset - 1)
      ?.parentOfTypes(MarkdownBlockQuote::class, MarkdownCodeFence::class) != null
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
    val marker = marker ?: return EnterHandlerDelegate.Result.Continue // for smart cast

    val document = editor.document
    runWriteAction {
      EditorModificationUtil.insertStringAtCaret(editor, marker)
    }

    PsiDocumentManager.getInstance(file.project).commitDocument(document)

    (file as MarkdownFile).getListItemAt(editor.caretModel.offset, document)!!
      .list.renumberInBulk(document, recursive = false, restart = false)

    // it is possible that there will be no pre-processing before next post-processing, see IDEA-270501 and EnterInLineCommentHandler
    this.marker = null
    return EnterHandlerDelegate.Result.Stop
  }
}
