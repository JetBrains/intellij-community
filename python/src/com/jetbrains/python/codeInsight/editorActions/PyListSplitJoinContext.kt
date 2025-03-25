// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.editorActions

import com.intellij.openapi.editor.actions.lists.CommaListSplitJoinContext
import com.intellij.openapi.editor.actions.lists.JoinOrSplit
import com.intellij.openapi.editor.actions.lists.ListWithElements
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class PyListSplitJoinContext : CommaListSplitJoinContext() {
  override fun extractData(context: PsiElement): ListWithElements? {
    val parent = PsiTreeUtil.findFirstParent(context) {
      // This is needed only for intention to work before the first and after the last element of a tuple
      (it is PyParenthesizedExpression && it.containedExpression is PyTupleExpression)
      || it is PySequenceExpression || it is PyParameterList || it is PyArgumentList
    }.let { if (it is PyParenthesizedExpression) it.containedExpression else it }

    return when (parent) {
      is PySequenceExpression -> ListWithElements(parent, parent.elements.toList())
      is PyParameterList -> ListWithElements(parent, parent.parameters.toList())
      is PyArgumentList -> ListWithElements(parent, parent.arguments.toList())
      else -> null
    }
  }

  override fun needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean {
    // if there is a trailing comma, add tail break
    return if (mode == JoinOrSplit.SPLIT) hasSeparatorAfter(data, lastElement) else super.needTailBreak(data, lastElement, mode)
  }

  override fun needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean {
    // if there is a trailing comma, also add head break
    return needTailBreak(data, data.elements.last(), mode)
  }

  override fun nextBreak(data: ListWithElements, element: PsiElement): PsiElement? {
    // in PyTupleExpression spaces for first/last elements are stored in parent psi
    val nextBreak = super.nextBreak(data, element)
    if (nextBreak != null || data.list !is PyTupleExpression || data.elements.last() != element) {
      return nextBreak
    }
    return super.nextBreak(data, data.list)
  }

  override fun prevBreak(data: ListWithElements, element: PsiElement): PsiElement? {
    // in PyTupleExpression spaces for first/last elements are stored in parent psi
    val prevBreak = super.prevBreak(data, element)
    if (prevBreak != null || data.list !is PyTupleExpression || data.elements.first() != element) {
      return prevBreak
    }
    return super.prevBreak(data, data.list)
  }

  override fun reformatRange(file: PsiFile, rangeToAdjust: TextRange, split: JoinOrSplit) {
    val tupleExpression = PsiTreeUtil.findElementOfClassAtRange(file, rangeToAdjust.startOffset, rangeToAdjust.endOffset, PyTupleExpression::class.java)
    if (tupleExpression != null && tupleExpression.parent is PyParenthesizedExpression) {
      CodeStyleManager.getInstance(file.project).reformat(tupleExpression.parent)
      return
    }

    CodeStyleManager.getInstance(file.project).reformatText(file, rangeToAdjust.startOffset, rangeToAdjust.endOffset)
  }
}