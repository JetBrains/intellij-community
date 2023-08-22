// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.surround.surrounders.expressions

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyForStatement
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStatementListContainer

class PyForExpressionSurrounder : PyExpressionAsConditionSurrounder() {
  @NlsSafe
  override fun getTemplateDescription(): String = "for e in expr"

  override fun getTextToGenerate(): String = "for i in expr:\n pass"

  override fun getCondition(statement: PyStatement?): PyExpression? {
    return (statement as? PyForStatement)?.forPart?.source
  }

  override fun getStatementListContainer(statement: PyStatement?): PyStatementListContainer? {
    return (statement as? PyForStatement)?.forPart
  }
}
