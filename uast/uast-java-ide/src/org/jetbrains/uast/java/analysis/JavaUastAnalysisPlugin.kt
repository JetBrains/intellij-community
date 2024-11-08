// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.analysis

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.CommonDataflow
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.analysis.UastAnalysisPlugin

class JavaUastAnalysisPlugin : UastAnalysisPlugin {
  override val language: JavaLanguage = JavaLanguage.INSTANCE

  override fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T? {
    when (fact) {
      is UExpressionFact.UNullabilityFact -> {
        @Suppress("UNCHECKED_CAST")
        return getNullability(sourcePsi) as T
      }
    }
  }

  private fun getNullability(psiElement: PsiElement?): UNullability? =
    when (psiElement) {
      is PsiTypeElement -> getNullabilityForTypeReference(psiElement)
      is PsiExpression -> getNullabilityForExpression(psiElement)
      else -> null
    }

  private fun getNullabilityForTypeReference(typeElement: PsiTypeElement): UNullability? {
    val modifierListOwner = typeElement.findParentOfType<PsiModifierListOwner>() ?: return null
    return when (NullableNotNullManager.getNullability(modifierListOwner)) {
      Nullability.NOT_NULL -> UNullability.NOT_NULL
      Nullability.NULLABLE -> UNullability.NULLABLE
      Nullability.UNKNOWN -> UNullability.UNKNOWN
    }
  }

  private fun getNullabilityForExpression(expression: PsiExpression): UNullability? {
    val dfType = CommonDataflow.getDfType(expression)
    return DfaNullability.fromDfType(dfType).toUNullability()
  }

  private fun DfaNullability.toUNullability() = when (this) {
    DfaNullability.NULL -> UNullability.NULL
    DfaNullability.NULLABLE -> UNullability.NULLABLE
    DfaNullability.NOT_NULL -> UNullability.NOT_NULL
    DfaNullability.UNKNOWN, DfaNullability.FLUSHED -> UNullability.UNKNOWN
  }
}