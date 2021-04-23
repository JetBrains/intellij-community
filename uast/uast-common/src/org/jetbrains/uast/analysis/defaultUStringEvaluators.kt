// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.uast.callExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getQualifiedChain

object UStringBuilderEvaluator : UStringEvaluator.BuilderLikeExpressionEvaluator<PartiallyKnownString?> {
  override val buildMethod: ElementPattern<PsiMethod>
    get() = PsiJavaPatterns.psiMethod().withName("toString").definedInClass("java.lang.StringBuilder")

  override val allowSideEffects: Boolean
    get() = true

  override fun isExpressionReturnSelf(expression: UReferenceExpression): Boolean {
    val qualifiedChain = expression.getQualifiedChain()
    val callPattern = callExpression().withAnyResolvedMethod(PsiJavaPatterns.psiMethod().definedInClass("java.lang.StringBuilder"))
    return qualifiedChain.firstOrNull() is USimpleNameReferenceExpression && qualifiedChain.drop(1).all { callPattern.accepts(it) }
  }

  override val methodDescriptions: Map<ElementPattern<PsiMethod>, (UCallExpression, PartiallyKnownString?, UStringEvaluator, UStringEvaluator.Configuration) -> PartiallyKnownString?>
    get() = mapOf(
      PsiJavaPatterns.psiMethod().withName("append").definedInClass("java.lang.StringBuilder") to { call, currentResult, stringEvaluator, config ->
        val entries = currentResult?.segments?.toMutableList() ?: mutableListOf<StringEntry>(
          StringEntry.Unknown(call.sourcePsi!!, TextRange(0, 1)))
        val argument = call.getArgumentForParameter(0)?.let { argument ->
          stringEvaluator.calculateValue(argument, config)
        }
        if (argument != null) {
          entries.addAll(argument.segments)
        }

        PartiallyKnownString(entries)
      },
      PsiJavaPatterns.psiMethod().definedInClass("java.lang.StringBuilder").constructor(true) to { call, _, stringEvaluator, config ->
        call.getArgumentForParameter(0)?.let { argument ->
          stringEvaluator.calculateValue(argument, config)
        }
      }
    )
}