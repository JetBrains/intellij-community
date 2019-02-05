// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.surround.surrounders.expressions.PyLenExpressionStatementSurrounder

class PyLenPostfixTemplate : SurroundPostfixTemplateBase("len", DESCR, PyPostfixUtils.PY_PSI_INFO,
                                                         PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(sizedFilter)) {

  override fun getSurrounder(): Surrounder = PyLenExpressionStatementSurrounder()

  companion object {
    const val DESCR = "len(expr)"

    val sizedFilter: Condition<PsiElement> = Condition { element ->
      val ref = element.reference
      if (ref?.resolve() is PyClass)
        return@Condition false

      val expression = element as PyExpression
      val context = TypeEvalContext.codeCompletion(expression.project, expression.containingFile)
      val type = context.getType(expression) ?: return@Condition false

      val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
      val results = type.resolveMember(PyNames.LEN, null, AccessDirection.READ, resolveContext)
      return@Condition results?.isNotEmpty() ?: false
    }
  }
}
