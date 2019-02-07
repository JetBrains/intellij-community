// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.expressions

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.uast.*
import java.util.*


class UStringConcatenationsFacade private constructor(context: PsiElement) {

  val uastOperands: Sequence<UExpression> = run {
    val uElement = context.toUElement(UExpression::class.java) ?: return@run emptySequence()
    when {
      uElement is UPolyadicExpression -> uElement.operands.asSequence()
      uElement is ULiteralExpression && uElement.uastParent !is UPolyadicExpression -> {
        val host = uElement.psiLanguageInjectionHost
        if (host !== context || !host.isValidHost) emptySequence() else sequenceOf(uElement)
      }
      else -> emptySequence()
    }
  }

  val psiLanguageInjectionHosts: Sequence<PsiLanguageInjectionHost> =
    uastOperands.mapNotNull { (it as? ULiteralExpression)?.psiLanguageInjectionHost }.distinct()

  /**
   * external (non string-literal) expressions in string interpolations and/or concatenations
   */
  val placeholders: List<Pair<TextRange, String>> by lazy(LazyThreadSafetyMode.NONE) {
    ArrayList<Pair<TextRange, String>>().apply {
      val operandsList = uastOperands.toList()
      val bounds = context.textRange
      for (i in operandsList.indices) {
        val operand = operandsList[i]
        if (operand !is ULiteralExpression) {
          val prev = operandsList.getOrNull(i - 1)
          val next = operandsList.getOrNull(i + 1)
          val sourcePsi = operand.sourcePsi
          val selfRange = sourcePsi?.textRange ?: continue
          val start = when (prev) {
            null -> bounds.startOffset
            is ULiteralExpression -> prev.sourcePsi?.textRange?.endOffset ?: selfRange.startOffset
            else -> selfRange.startOffset
          }
          val end = when (next) {
            null -> bounds.endOffset
            is ULiteralExpression -> next.sourcePsi?.textRange?.startOffset ?: selfRange.endOffset
            else -> selfRange.endOffset
          }
          val range = TextRange.create(start, end)
          val evaluate = operand.evaluate()?.toString() ?: "missingValue"

          add(range to evaluate)
        }
      }
    }
  }


  companion object {
    @JvmStatic
    fun create(context: PsiElement): UStringConcatenationsFacade? {
      if (context !is PsiLanguageInjectionHost && context.firstChild !is PsiLanguageInjectionHost) {
        return null
      }
      return UStringConcatenationsFacade(context)
    }
  }

}

