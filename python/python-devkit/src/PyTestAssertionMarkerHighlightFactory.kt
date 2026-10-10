// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer

/**
 * Highlights the code that a marker of the `PyCodeInsightTestCase` assertion language points at.
 *
 * It works when the caret is on the first line of a marker assertion in an injected Python text.
 */
internal class PyTestAssertionMarkerHighlightFactory : HighlightUsagesHandlerFactoryBase() {

  override fun createHighlightUsagesHandler(editor: Editor, psiFile: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
    if (target !is PsiComment) return null
    val testText = PyTestCaseText.of(psiFile) ?: return null
    val assertion = testText.assertionsStartingIn(target.textRange).firstOrNull() ?: return null
    val markerRange = testText.markerRange(assertion) ?: return null
    val codeRange = testText.codeRange(assertion) ?: return null
    return MarkerHighlightHandler(editor, psiFile, target, markerRange, codeRange)
  }

  private class MarkerHighlightHandler(
    editor: Editor,
    file: PsiFile,
    private val comment: PsiComment,
    private val markerRange: TextRange,
    private val codeRange: TextRange,
  ) : HighlightUsagesHandlerBase<PsiComment>(editor, file) {

    override fun getTargets(): List<PsiComment> = listOf(comment)

    override fun selectTargets(targets: List<PsiComment>, selectionConsumer: Consumer<in List<PsiComment>>) {
      selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: List<PsiComment>) {
      myReadUsages += markerRange
      myReadUsages += codeRange
    }

    override fun highlightReferences(): Boolean = false
  }
}
