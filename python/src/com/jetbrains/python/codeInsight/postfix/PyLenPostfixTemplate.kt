// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.postfix

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

class PyLenPostfixTemplate : SurroundPostfixTemplateBase("len", DESCR, PyPostfixUtils.PY_PSI_INFO,
                                                         PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(sizedFilter)) {

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
