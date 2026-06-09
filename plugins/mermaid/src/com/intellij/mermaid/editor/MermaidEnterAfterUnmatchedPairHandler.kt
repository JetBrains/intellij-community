// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.codeInsight.highlighting.BraceMatcher
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.lang.Language
import com.intellij.mermaid.lang.formatter.MermaidSemanticEditorPosition
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import java.lang.Integer.max

internal class MermaidEnterAfterUnmatchedPairHandler : EnterHandlerDelegateAdapter() {
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
    position.moveBeforeOptionalMix(MermaidTokens.WHITE_SPACE)

    position.moveBeforeOptionalMix(
      MermaidTokens.ID,
      MermaidTokens.Sequence.CONTROL_ID,
      MermaidTokens.LEFT_OF,
      MermaidTokens.RIGHT_OF,
      MermaidTokens.WHITE_SPACE
    )
    val atComplexNote = position.isAt(MermaidTokens.NOTE)

    val maxRBraceCount = getMaxRBraceCount(file, editor, offset)
    if (maxRBraceCount > 0) {
      if (atComplexNote) {
        document.insertString(offset, "\nend note")
      } else {
        document.insertString(offset, "\nend")
      }
    }

    return Result.Continue
  }

  private fun getMaxRBraceCount(file: PsiFile, editor: Editor, caretOffset: Int): Int {
    return max(
      0,
      getUnmatchedLBracesNumberBefore(editor, caretOffset, file.fileType)
    )
  }

  private fun getUnmatchedLBracesNumberBefore(editor: Editor, offset: Int, fileType: FileType): Int {
    if (offset == 0) {
      return -1
    }

    val document = editor.document
    val chars = document.charsSequence

    var position: MermaidSemanticEditorPosition = getPosition(editor, offset - 1)
    var iterator = position.iterator

    position.moveBeforeOptionalMix(
      MermaidTokens.ID,
      MermaidTokens.LEFT_OF,
      MermaidTokens.RIGHT_OF,
      MermaidTokens.Sequence.MESSAGE,
      MermaidTokens.WHITE_SPACE
    )

    val braceMatcher: BraceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator)

    if (!braceMatcher.isLBraceToken(iterator, chars, fileType) || !braceMatcher.isStructuralBrace(
        iterator,
        chars,
        fileType
      )
    ) {
      return -1
    }

    val language: Language = iterator.tokenType.language

    position = getPosition(editor, 0)
    iterator = position.iterator
    var lBracesBeforeOffset = 0
    var rBracesAfterOffset = 0

    while (!iterator.atEnd()) {
      if (iterator.tokenType.language != language || !braceMatcher.isStructuralBrace(iterator, chars, fileType)) {
        iterator.advance()
        continue
      }

      val beforeOffset = iterator.start < offset

      if (braceMatcher.isLBraceToken(iterator, chars, fileType)) {
        if (beforeOffset) {
          lBracesBeforeOffset++
        } else {
          rBracesAfterOffset--
        }
      } else if (braceMatcher.isRBraceToken(iterator, chars, fileType)) {
        if (beforeOffset) {
          // If there are more right braces then left - code before is invalid but let's not break the code after.
          if (lBracesBeforeOffset > 0) {
            lBracesBeforeOffset--
          }
        } else {
          rBracesAfterOffset++
          if (rBracesAfterOffset == lBracesBeforeOffset) {
            // Do not calculate further. We've completed all scopes before cursor.
            return 0
          }
        }
      }

      iterator.advance()
    }
    return lBracesBeforeOffset - rBracesAfterOffset
  }

  private fun getPosition(editor: Editor, offset: Int): MermaidSemanticEditorPosition {
    return MermaidSemanticEditorPosition.createEditorPosition(editor, offset)
  }
}
