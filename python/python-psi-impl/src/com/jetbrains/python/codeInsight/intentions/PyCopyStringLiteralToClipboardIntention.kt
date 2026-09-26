// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyCopyStringLiteralToClipboardIntention : PsiBasedModCommandAction<PyStringLiteralExpression>(PyStringLiteralExpression::class.java) {
  override fun getFamilyName(): String = PyPsiBundle.message("INTN.NAME.copy.string.to.clipboard")

  override fun getPresentation(context: ActionContext, element: PyStringLiteralExpression): Presentation =
    Presentation.of(PyPsiBundle.message("INTN.NAME.copy.string.to.clipboard"))

  override fun perform(context: ActionContext, element: PyStringLiteralExpression): ModCommand =
    ModCommand.copyToClipboard(buildStringText(element))

  private fun buildStringText(expression: PyStringLiteralExpression): String {
    if (!expression.isInterpolated) return expression.stringValue
    return buildString {
      for (element in expression.stringElements) {
        if (element is PyFormattedStringElement) {
          val literalRanges = element.literalPartRanges
          for ((range, text) in element.decodedFragments) {
            if (literalRanges.any { it.containsRange(range.startOffset, range.endOffset) }) append(text)
            else append('?')
          }
        }
        else {
          for ((_, text) in element.decodedFragments) append(text)
        }
      }
    }
  }
}
