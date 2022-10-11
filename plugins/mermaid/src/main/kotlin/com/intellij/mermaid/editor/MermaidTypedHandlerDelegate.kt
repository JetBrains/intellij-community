package com.intellij.mermaid.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidAttribute
import com.intellij.mermaid.lang.psi.MermaidClassStatement
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.MermaidIdentifier
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.impl.TypedActionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType

class MermaidTypedHandlerDelegate : TypedHandlerDelegate() {
  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file !is MermaidFile) return Result.CONTINUE

    val caretModel = editor.caretModel
    val offset = caretModel.offset

    val document = editor.document

    if (c == '<' && offset - 2 >= 0 && document.getText(TextRange(offset - 2, offset)) == "<<") {
      writeText(editor, offset, ">>")
    }
    if (c == '~' && offset - 2 >= 0) {
      val element: PsiElement = prevNonWhiteSpaceToken(file, offset - 2) ?: return Result.CONTINUE

      val parent = element.parent
      if (element.elementType == MermaidTokens.ID && parent?.parent is MermaidAttribute) {
        writeText(editor, offset, "~")
      }
      if (parent is MermaidIdentifier && parent.parent is MermaidClassStatement) {
        writeText(editor, offset, "~")
      }
    }

    return Result.CONTINUE
  }

  private fun prevNonWhiteSpaceToken(file: PsiFile, offset: Int): PsiElement? {
    var element: PsiElement? = file.findElementAt(offset - 2)

    while (element?.elementType in TokenSet.create(MermaidTokens.WHITE_SPACE, TokenType.WHITE_SPACE)) {
      element = element?.prevSibling
    }
    return element
  }

  private fun writeText(editor: Editor, offset: Int, text: String) {
    val typedAction = TypedAction.getInstance() as TypedActionImpl
    runWriteAction {
      typedAction.defaultRawTypedHandler.beginUndoablePostProcessing()
      editor.document.insertString(offset, text)
      editor.caretModel.moveToOffset(offset)
      editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
      editor.selectionModel.removeSelection()
    }
  }
}
