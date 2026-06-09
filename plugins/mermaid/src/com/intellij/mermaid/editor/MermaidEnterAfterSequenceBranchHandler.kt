// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.mermaid.lang.formatter.MermaidSemanticEditorPosition
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCoreUtil
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile

internal class MermaidEnterAfterSequenceBranchHandler : EnterHandlerDelegateAdapter() {
  override fun preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffset: Ref<Int>,
    caretAdvance: Ref<Int>,
    dataContext: DataContext,
    originalHandler: EditorActionHandler?
  ): Result {
    if (file !is MermaidFile) {
      return Result.Continue
    }

    val offset = caretOffset.get()
    val document = editor.document

    val position = getPosition(editor, offset - 1)
    position.moveBeforeOptionalMix(MermaidTokens.WHITE_SPACE, MermaidTokens.Sequence.MESSAGE)
    if (!position.isAtAnyOf(
        MermaidTokens.Sequence.ELSE,
        MermaidTokens.Sequence.AND,
        MermaidTokens.Sequence.OPTION
      )
    ) return Result.Continue

    val currentStartOffset = position.iterator.start
    val currentIndent = currentStartOffset - document.getLineStartOffset(document.getLineNumber(currentStartOffset))

    position.moveBeforeNotAtOptionalMix(
      MermaidTokens.Sequence.ALT,
      MermaidTokens.Sequence.PAR,
      MermaidTokens.Sequence.PAR_OVER,
      MermaidTokens.Sequence.CRITICAL
    )
    if (position.iterator.atEnd()) return Result.Continue

    val outerStartOffset = position.iterator.start
    val parentIndent = outerStartOffset - document.getLineStartOffset(document.getLineNumber(outerStartOffset))

    val indent = parentIndent - currentIndent

    val newCaretOffset = EditorCoreUtil.indentLine(
      editor.project,
      editor,
      document.getLineNumber(offset),
      indent,
      offset,
      true
    )
    caretOffset.set(newCaretOffset)

    return Result.Continue
  }

  private fun getPosition(editor: Editor, offset: Int): MermaidSemanticEditorPosition {
    return MermaidSemanticEditorPosition.createEditorPosition(editor, offset)
  }
}
