// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyTupleExpression
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WrapExceptTupleInParenthesesQuickFix(exceptPartTuple: PyTupleExpression)
  : PsiUpdateModCommandAction<PyTupleExpression>(exceptPartTuple) {

  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.except.clause.missing.parens")

  override fun invoke(context: ActionContext, exceptPartTuple: PyTupleExpression, updater: ModPsiUpdater) {
    val generator = PyElementGenerator.getInstance(context.project)
    val level = LanguageLevel.forElement(exceptPartTuple)
    val wrapped = generator.createExpressionFromText(level, "(${exceptPartTuple.text})")
    exceptPartTuple.replace(wrapped)
  }
}