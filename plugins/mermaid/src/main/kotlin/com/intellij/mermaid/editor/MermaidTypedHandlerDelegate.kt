package com.intellij.mermaid.editor

import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.impl.TypedActionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class MermaidTypedHandlerDelegate : TypedHandlerDelegate() {
  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file !is MermaidFile) return Result.CONTINUE

    val caretModel = editor.caretModel
    val offset = caretModel.offset

    val document = editor.document
    val typedAction = TypedAction.getInstance() as TypedActionImpl

    if (c == '<' && offset - 2 >= 0 && document.getText(TextRange(offset - 2, offset)) == "<<") {
      runWriteAction {
        typedAction.defaultRawTypedHandler.beginUndoablePostProcessing()
        document.insertString(offset, ">>")
        caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        editor.selectionModel.removeSelection()
      }
    }
    if (c == '~') {
      runWriteAction {
        typedAction.defaultRawTypedHandler.beginUndoablePostProcessing()
        document.insertString(offset, "~")
        caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        editor.selectionModel.removeSelection()
      }
    }

    return Result.CONTINUE
  }
}
