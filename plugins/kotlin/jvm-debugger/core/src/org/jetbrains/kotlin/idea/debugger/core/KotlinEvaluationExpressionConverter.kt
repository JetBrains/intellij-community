// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.DebuggerEvaluationExpressionConverter
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiTypeCastExpression
import org.jetbrains.kotlin.idea.KotlinFileType

/**
 * Creates a Kotlin [TextWithImports] from a Java [PsiElement] produced by the debugger tree evaluation chain,
 * replacing Java type casts like `((Type)expr).field` with Kotlin casts (e.g., `(expr as Type).field` for Kotlin).
 */
internal class KotlinEvaluationExpressionConverter : DebuggerEvaluationExpressionConverter {
  override fun convert(psiExpression: PsiElement): TextWithImports {
    val text = convertToKotlin(psiExpression)
    val imports = psiExpression.getUserData(DebuggerTreeNodeExpression.ADDITIONAL_IMPORTS_KEY)?.let {
        StringUtil.join(it, ",")
    } ?: ""
    return TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text, imports, KotlinFileType.INSTANCE)
  }

  private fun convertToKotlin(element: PsiElement): String = when (element) {
    is PsiTypeCastExpression -> {
      val type = element.castType?.text ?: return element.text
      val operand = element.operand ?: return element.text
      "${convertToKotlin(operand)} as $type"
    }
    is PsiParenthesizedExpression -> {
      val inner = element.expression ?: return "()"
      "(${convertToKotlin(inner)})"
    }
    else -> {
      val children = element.children
      if (children.isEmpty()) element.text
      else children.joinToString("") { convertToKotlin(it) }
    }
  }
}
