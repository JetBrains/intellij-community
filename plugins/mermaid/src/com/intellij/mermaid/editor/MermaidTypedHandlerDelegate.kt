// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.mermaid.lang.formatter.MermaidSemanticEditorPosition
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.impl.TypedActionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType

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
      if (testPreviousNonWhiteSpaceToken(editor, offset - 2) {
          it.isAtAnyOf(
            MermaidTokens.ATTRIBUTE_WORD,
            MermaidTokens.ClassDiagram.CLASS_ID
          )
        }) {
        writeText(editor, offset, "~")
      }
    }
    if (c == '|' && offset - 2 >= 0) {
      if (testPreviousNonWhiteSpaceToken(editor, offset - 2) { it.isAt(MermaidTokens.ARROW) }) {
        writeText(editor, offset, "|")
      }
    }
    if (c == '{' && offset - 3 >= 0 && document.getText(TextRange(offset - 3, offset)) == "%%{") {
      if (offset + 1 > document.textLength || document.getText(TextRange(offset - 3, offset + 1)) != "%%{}") {
        writeText(editor, offset, "}%%", offset)
      } else {
        writeText(editor, offset + 1, "%%", offset)
      }
    }

    return Result.CONTINUE
  }

  private fun testPreviousNonWhiteSpaceToken(
    editor: Editor,
    offset: Int,
    test: (MermaidSemanticEditorPosition) -> Boolean
  ): Boolean {
    val position = MermaidSemanticEditorPosition.createEditorPosition(editor, offset)
    position.moveBeforeOptionalMix(MermaidTokens.WHITE_SPACE, TokenType.WHITE_SPACE)
    return test(position)
  }

  private fun writeText(editor: Editor, offset: Int, text: String) {
    writeText(editor, offset, text, offset)
  }

  private fun writeText(editor: Editor, offset: Int, text: String, moveToOffset: Int) {
    val typedAction = TypedAction.getInstance() as TypedActionImpl
    runWriteAction {
      typedAction.defaultRawTypedHandler.beginUndoablePostProcessing()
      editor.document.insertString(offset, text)
      editor.caretModel.moveToOffset(moveToOffset)
      editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
      editor.selectionModel.removeSelection()
    }
  }
}
