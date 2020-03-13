// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.analysis

import com.intellij.codeInspection.dataFlow.CommonDataflow
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import com.intellij.psi.controlFlow.DefUseUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.ExpressionUtils
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.toUElement

class JavaUastAnalysisPlugin : UastAnalysisPlugin {
  override fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T? {
    when (fact) {
      is UExpressionFact.UNullabilityFact -> {
        val psiExpression = sourcePsi as? PsiExpression ?: return null
        val dfType = CommonDataflow.getDfType(psiExpression)
        val nullability = DfaNullability.fromDfType(dfType).toUNullability()

        @Suppress("UNCHECKED_CAST")
        return nullability as T
      }
    }
  }

  override fun UExpression.findIndirectUsages(): List<UExpression> {
    var expression = sourcePsi as? PsiExpression ?: return listOf(this)
    while (expression.parent is PsiPolyadicExpression || expression.parent is PsiParenthesizedExpression) {
      expression = expression.parent as PsiExpression
    }
    val passThrough = ExpressionUtils.getPassThroughExpression(expression)
    val parent = passThrough.parent
    val defaultResult = if (passThrough == expression) emptyList() else listOfNotNull(passThrough.toUElement(UExpression::class.java))
    var local : PsiLocalVariable? = null
    if (parent is PsiLocalVariable) {
      local = parent
    }
    else if (parent is PsiAssignmentExpression && parent.rExpression == passThrough && parent.operationTokenType == JavaTokenType.EQ) {
      local = ExpressionUtils.resolveLocalVariable(parent.lExpression)
    }
    if (local != null) {
      val codeBlock = PsiUtil.getVariableCodeBlock(local, null) as? PsiCodeBlock
      if (codeBlock != null) {
        return defaultResult +
               DefUseUtil.getRefs(codeBlock, local, passThrough).mapNotNull { e -> e.toUElement(UExpression::class.java) }
      }
    }
    return defaultResult
  }

  override val language = JavaLanguage.INSTANCE

  private fun DfaNullability.toUNullability() = when (this) {
    DfaNullability.NULL -> UNullability.NULL
    DfaNullability.NULLABLE -> UNullability.NULLABLE
    DfaNullability.NOT_NULL -> UNullability.NOT_NULL
    DfaNullability.UNKNOWN, DfaNullability.FLUSHED -> UNullability.UNKNOWN
  }
}