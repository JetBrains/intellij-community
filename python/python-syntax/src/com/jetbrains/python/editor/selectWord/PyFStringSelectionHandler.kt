// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.editor.selectWord

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.ast.PyAstExpression
import com.jetbrains.python.ast.PyAstFStringFragment
import com.jetbrains.python.ast.PyAstFormattedStringElement


class PyFStringSelectionHandler : ExtendWordSelectionHandlerBase() {

  override fun canSelect(e: PsiElement): Boolean {
    return PsiTreeUtil.getParentOfType(e, PyAstFStringFragment::class.java, false) != null
  }

  override fun select(
    e: PsiElement,
    editorText: CharSequence,
    cursorOffset: Int,
    editor: Editor
  ): List<TextRange>? {
    val fragment: PyAstFStringFragment =
      PsiTreeUtil.getParentOfType(e, PyAstFStringFragment::class.java, false)
      ?: return null

    val expression: PyAstExpression = fragment.expression ?: return null
    val exprRange: TextRange = expression.textRange

    val result = mutableListOf<TextRange>()

    result.add(exprRange)

    val typeConversionPsi = fragment.typeConversion
    if (typeConversionPsi != null) {
      val typeConversionRange = typeConversionPsi.textRange
      result.add(TextRange(exprRange.startOffset, typeConversionRange.endOffset))
    }

    val formatPartPsi = fragment.formatPart
    if (formatPartPsi != null) {
      val formatTextRange = formatPartPsi.textRange
      result.add(TextRange(exprRange.startOffset, formatTextRange.endOffset))
    }

    result.add(fragment.textRange)

    val parent = fragment.parent
    if (parent is PyAstFormattedStringElement) {
      result.add(parent.textRange)
    }
    return result
  }
}