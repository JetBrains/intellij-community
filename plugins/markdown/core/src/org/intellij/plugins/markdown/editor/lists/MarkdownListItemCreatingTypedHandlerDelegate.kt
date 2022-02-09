package org.intellij.plugins.markdown.editor.lists

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentRange
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * This handler renumbers the current list when you hit Enter after the number of some list item.
 */
internal class MarkdownListItemCreatingTypedHandlerDelegate : TypedHandlerDelegate() {

  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file !is MarkdownFile || c != ' '
        || !MarkdownSettings.getInstance(project).isEnhancedEditingEnabled) {
      return Result.CONTINUE
    }

    val document = editor.document
    PsiDocumentManager.getInstance(project).commitDocument(document)

    val caret = editor.caretModel.currentCaret

    val element = file.findElementAt(caret.offset - 1)
    if (element == null || element.elementType !in MarkdownTokenTypeSets.LIST_MARKERS) {
      return Result.CONTINUE
    }

    if (caret.offset <= document.getLineIndentRange(caret.logicalPosition.line).endOffset) {
      // so that entering a space before a list item won't renumber it
      return Result.CONTINUE
    }

    val markerEnd = element.endOffset - 1
    if (CharArrayUtil.shiftBackward(document.charsSequence, markerEnd, " ") < markerEnd - 1) {
      // so that entering a space after a marker won't turn children-items into siblings
      return Result.CONTINUE
    }

    element.parentOfType<MarkdownList>()!!.renumberInBulk(document, recursive = false, restart = false)
    PsiDocumentManager.getInstance(project).commitDocument(document)
    caret.moveToOffset(file.findElementAt(caret.offset - 1)?.endOffset ?: caret.offset)
    return Result.STOP
  }
}
