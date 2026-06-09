// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.codeInsight.highlighting.BraceMatcher
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.mermaid.lang.formatter.MermaidSemanticEditorPosition
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager

class MermaidEnterBetweenPairHandler : EnterHandlerDelegateAdapter() {
  override fun preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffset: Ref<Int>,
    caretAdvance: Ref<Int>,
    dataContext: DataContext,
    originalHandler: EditorActionHandler?
  ): EnterHandlerDelegate.Result {
    if (file !is MermaidFile || !CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER) {
      return EnterHandlerDelegate.Result.Continue
    }
    val document = editor.document
    val text = document.charsSequence
    val offset = caretOffset.get()

    if (!isApplicable(file, editor, text, offset)) {
      return EnterHandlerDelegate.Result.Continue
    }

    PsiDocumentManager.getInstance(file.project).commitDocument(document)
    CodeStyleManager.getInstance(file.project).adjustLineIndent(file, editor.caretModel.offset + 1)

    return EnterHandlerDelegate.Result.DefaultForceIndent
  }

  private fun isApplicable(
    file: PsiFile,
    editor: Editor,
    documentText: CharSequence,
    caretOffset: Int
  ): Boolean {
    return isValidOffset(caretOffset, documentText)
      && isLBraceToken(file, editor, documentText, caretOffset)
      && isRBraceToken(file, editor, documentText, caretOffset)
  }

  private fun isLBraceToken(
    file: PsiFile,
    editor: Editor,
    documentText: CharSequence,
    caretOffset: Int
  ): Boolean {
    val positionLeft: MermaidSemanticEditorPosition = getPosition(editor, caretOffset - 1)
    positionLeft.moveBeforeOptionalMix(
      MermaidTokens.ID,
      MermaidTokens.Sequence.MESSAGE,
      MermaidTokens.WHITE_SPACE,
      MermaidTokens.RIGHT_OF,
      MermaidTokens.LEFT_OF
    )

    val iteratorLeft = positionLeft.iterator
    val leftBraceMatcher: BraceMatcher = BraceMatchingUtil.getBraceMatcher(file.fileType, iteratorLeft)
    return leftBraceMatcher.isLBraceToken(iteratorLeft, documentText, file.fileType)
      && leftBraceMatcher.isStructuralBrace(iteratorLeft, documentText, file.fileType)
  }

  private fun isRBraceToken(
    file: PsiFile,
    editor: Editor,
    documentText: CharSequence,
    caretOffset: Int
  ): Boolean {
    val positionRight: MermaidSemanticEditorPosition = getPosition(editor, caretOffset)
    positionRight.moveAfterOptionalMix(MermaidTokens.EOL, MermaidTokens.WHITE_SPACE)

    val iteratorRight = positionRight.iterator
    val rightBraceMatcher: BraceMatcher = BraceMatchingUtil.getBraceMatcher(file.fileType, iteratorRight)
    return rightBraceMatcher.isRBraceToken(iteratorRight, documentText, file.fileType)
      && rightBraceMatcher.isStructuralBrace(iteratorRight, documentText, file.fileType)
  }

  private fun getPosition(editor: Editor, offset: Int): MermaidSemanticEditorPosition {
    return MermaidSemanticEditorPosition.createEditorPosition(editor, offset)
  }

  private fun isValidOffset(offset: Int, text: CharSequence): Boolean {
    return offset in text.indices && (offset - 1) in text.indices
  }
}
