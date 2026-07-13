// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PsiUtil")

package com.intellij.debugger.streams.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore

internal fun ignoreWhiteSpaces(element: PsiElement): PsiElement =
  PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace::class.java)
  ?: PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace::class.java)
  ?: element

internal fun findPsiMethodCall(psiFile: PsiFile, position: TextRange): PsiMethodCallExpression? {
  var element: PsiElement? = PsiUtilCore.getElementAtOffset(psiFile, position.endOffset - 1)
  while (element != null) {
    if (element is PsiMethodCallExpression && element.textRange == position) {
      return element
    }
    element = element.parent
  }
  return null
}