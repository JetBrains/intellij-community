// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.python.testAssertions.PyTestAssertionCompletion

/**
 * Completes the assertion language of `PyCodeInsightTestCase` in a Python text that is injected into a test class of the monorepo.
 *
 * The string must go to a parameter with `@PyTestAssertionText`, as [PyTestCaseText] checks.
 * [PyTestAssertionCompletion] computes the items.
 * The module of this class depends on Plugin DevKit, so the contributor works only when DevKit is installed.
 */
internal class PyTestAssertionCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.completionType != CompletionType.BASIC) return
    if (!isInsideComment(parameters.position)) return
    if (PyTestCaseText.of(parameters.originalFile) == null) return

    val document = parameters.editor.document
    val offset = parameters.offset
    if (offset < 0 || offset > document.textLength) return
    val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
    val completion = PyTestAssertionCompletion.complete(document.charsSequence.subSequence(lineStart, offset).toString()) ?: return

    val resultWithPrefix = result.withPrefixMatcher(completion.prefix)
    for ((text, tailText, typeText) in completion.items) {
      resultWithPrefix.addElement(LookupElementBuilder.create(text).withTailText(tailText, true).withTypeText(typeText, true))
    }
  }

  private fun isInsideComment(position: PsiElement): Boolean {
    if (PsiTreeUtil.getParentOfType(position, PsiComment::class.java, false) != null) return true
    val offsetBefore = position.textRange.startOffset - 1
    if (offsetBefore < 0) return false
    val elementBefore = PsiUtilCore.getElementAtOffset(position.containingFile, offsetBefore)
    return PsiTreeUtil.getParentOfType(elementBefore, PsiComment::class.java, false) != null
  }
}
