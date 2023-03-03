// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.surround.surrounders.expressions.PyLenExpressionStatementSurrounder

class PyLenPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
  "len", DESCR, PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(sizedFilter), provider) {

  override fun getSurrounder(): Surrounder = PyLenExpressionStatementSurrounder()

  companion object {
    const val DESCR = "len(expr)"

    val sizedFilter: Condition<PsiElement> = Condition { element ->

      if (!DumbService.isDumb(element.project)) {
        val expression = element as PyExpression
        val context = TypeEvalContext.codeCompletion(expression.project, expression.containingFile)
        val type = context.getType(expression) ?: return@Condition false
        return@Condition PyABCUtil.isSubtype(type, PyNames.SIZED, context)
      }
      return@Condition false
    }
  }
}
