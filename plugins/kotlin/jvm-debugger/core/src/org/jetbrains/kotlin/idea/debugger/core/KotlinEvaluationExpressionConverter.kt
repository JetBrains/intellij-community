// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.DebuggerEvaluationExpressionConverter
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiTypeElement
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
      val castType = element.castType ?: return element.text
      val operand = element.operand ?: return element.text
      "${convertToKotlin(operand)} as ${renderCastType(castType)}"
    }
    is PsiParenthesizedExpression -> {
      val inner = element.expression ?: return "()"
      "(${convertToKotlin(inner)})"
    }
    is PsiReferenceExpression -> {
      // A plugin may rename the field (atomicfu: `_state` to `_state$volatile`); prefill the source name.
      val name = element.referenceName
      val displayName = name?.let { KotlinMangledMemberContributor.getDisplayName(it) }
      val qualifier = element.qualifierExpression
      if (displayName != null && qualifier != null) {
        "${convertToKotlin(qualifier)}.$displayName"
      } else {
        val children = element.children
        if (children.isEmpty()) element.text else children.joinToString("") { convertToKotlin(it) }
      }
    }
    else -> {
      val children = element.children
      if (children.isEmpty()) element.text
      else children.joinToString("") { convertToKotlin(it) }
    }
  }

  /**
   * Java runtime-type casts are raw: generic arguments are dropped. A raw Kotlin cast such as `x as Box`
   * does not compile for a generic class, so re-add star projections, e.g. `x as Box<*>` or
   * `x as Map<*, *>` (IDEA-335226).
   *
   * We only need the arity, straight from the resolved Java [PsiClassType]. No Analysis API here: its
   * `defaultTypeWithStarProjections` wants a `KaClassifierSymbol` and an `analyze {}` session. Overkill for
   * appending `*`s in a PSI to text step.
   */
  private fun renderCastType(castType: PsiTypeElement): String {
    val text = castType.text
    if ('<' in text) return text
    val psiClass = (castType.type as? PsiClassType)?.resolve() ?: return text
    val arity = psiClass.typeParameters.size
    if (arity == 0) return text
    return text + (0 until arity).joinToString(separator = ", ", prefix = "<", postfix = ">") { "*" }
  }
}
